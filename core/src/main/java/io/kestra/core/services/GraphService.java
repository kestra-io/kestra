package io.kestra.core.services;

import java.util.*;
import java.util.stream.Stream;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.hierarchies.*;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.runners.DisplayExpressionResolver;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.RunVariables;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.PebbleUtil;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
@Slf4j
public class GraphService {
    private final FlowRepositoryInterface flowRepository;
    private final TriggerRepositoryInterface triggerRepository;
    private final PluginDefaultService pluginDefaultService;
    private final RunContextFactory runContextFactory;
    private final DisplayExpressionResolver displayExpressionResolver;
    private final VariableRenderer variableRenderer;

    @Inject
    public GraphService(
        FlowRepositoryInterface flowRepository,
        TriggerRepositoryInterface triggerRepository,
        PluginDefaultService pluginDefaultService,
        RunContextFactory runContextFactory,
        DisplayExpressionResolver displayExpressionResolver,
        VariableRenderer variableRenderer
    ) {
        this.flowRepository = flowRepository;
        this.triggerRepository = triggerRepository;
        this.pluginDefaultService = pluginDefaultService;
        this.runContextFactory = runContextFactory;
        this.displayExpressionResolver = displayExpressionResolver;
        this.variableRenderer = variableRenderer;
    }

    public FlowGraph flowGraph(FlowWithSource flow, List<String> expandedSubflows) throws IllegalVariableEvaluationException, FlowProcessingException {
        return this.flowGraph(flow, expandedSubflows, null);
    }

    public FlowGraph flowGraph(FlowWithSource flow, List<String> expandedSubflows, Execution execution) throws IllegalVariableEvaluationException, FlowProcessingException {
        return FlowGraph.of(this.of(flow, Optional.ofNullable(expandedSubflows).orElse(Collections.emptyList()), new HashMap<>(), execution));
    }

    public FlowGraph executionGraph(FlowWithSource flow, List<String> expandedSubflows, Execution execution) throws IllegalVariableEvaluationException, FlowProcessingException {
        return FlowGraph.of(this.of(flow, Optional.ofNullable(expandedSubflows).orElse(Collections.emptyList()), new HashMap<>(), execution));
    }

    public GraphCluster of(FlowWithSource flow, List<String> expandedSubflows, Map<String, FlowWithSource> flowByUid, Execution execution)
        throws IllegalVariableEvaluationException, FlowProcessingException {
        return this.of(null, flow, expandedSubflows, flowByUid, execution);
    }

    public GraphCluster of(GraphCluster baseGraph, FlowWithSource flow, List<String> expandedSubflows, Map<String, FlowWithSource> flowByUid)
        throws IllegalVariableEvaluationException, FlowProcessingException {
        return this.of(baseGraph, flow, expandedSubflows, flowByUid, null);
    }

    @SneakyThrows
    public GraphCluster of(GraphCluster baseGraph, FlowWithSource flow, List<String> expandedSubflows, Map<String, FlowWithSource> flowByUid, Execution execution)
        throws IllegalVariableEvaluationException, FlowProcessingException {
        String tenantId = flow.getTenantId();
        flow = pluginDefaultService.injectAllDefaults(flow, false);
        List<TriggerState> triggers = null;
        if (flow.getTriggers() != null) {
            triggers = triggerRepository.find(Pageable.UNPAGED, null, tenantId, flow.getNamespace(), flow.getId(), null);
        }
        GraphCluster graphCluster = GraphUtils.of(baseGraph, flow, execution, triggers);

        // Build a display variable context and attach resolved properties to each task node.
        var displayVariables = buildDisplayVariables(flow, execution);
        GraphUtils.nodes(graphCluster).stream()
            .filter(AbstractGraphTask.class::isInstance)
            .map(AbstractGraphTask.class::cast)
            .filter(node -> node.getTask() instanceof Task)
            .forEach(node ->
            {
                try {
                    var resolved = displayExpressionResolver.resolveProperties((Task) node.getTask(), displayVariables);
                    node.withRenderedProperties(resolved);
                } catch (Exception e) {
                    log.debug("Could not resolve display properties for task {}: {}", node.getTask().getId(), e.getMessage());
                }
            });

        Stream<Map.Entry<GraphCluster, SubflowGraphTask>> subflowToReplaceByParent = graphCluster.allNodesByParent().entrySet().stream()
            .flatMap(entry ->
            {
                List<SubflowGraphTask> subflowGraphTasks = entry.getValue().stream()
                    .filter(node -> node instanceof SubflowGraphTask && expandedSubflows.contains(node.getUid()))
                    .map(SubflowGraphTask.class::cast)
                    .toList();

                if (subflowGraphTasks.isEmpty()) {
                    return Stream.empty();
                }

                return subflowGraphTasks.stream().map(subflowGraphTask -> Map.entry(entry.getKey(), subflowGraphTask));
            });

        FlowWithSource finalFlow = flow;
        subflowToReplaceByParent.map(throwFunction(parentWithSubflowGraphTask ->
        {
            SubflowGraphTask subflowGraphTask = parentWithSubflowGraphTask.getValue();
            Task task = (Task) subflowGraphTask.getTask();
            RunContext runContext = PebbleUtil.containsOpeningBlockDelimiter(subflowGraphTask.executableTask().subflowId().flowUid()) && execution != null
                ? runContextFactory.of(finalFlow, task, execution, subflowGraphTask.getTaskRun())
                : null;
            subflowGraphTask = subflowGraphTask.withRenderedSubflowId(runContext);
            ExecutableTask.SubflowId subflowId = subflowGraphTask.executableTask().subflowId();

            if (PebbleUtil.containsOpeningBlockDelimiter(subflowId.flowUid())) {
                throw new IllegalArgumentException(
                    "Can't expand subflow task '" + task.getId() + "' because namespace and/or flowId contains dynamic values. This can only be viewed on an execution."
                );
            }

            FlowWithSource subflow = flowByUid.computeIfAbsent(
                subflowId.flowUid(),
                uid ->
                {
                    Optional<FlowWithSource> flowById;
                    // Prevent the need for FLOW READ access in case we're looking at an execution graph
                    if (execution != null) {
                        flowById = flowRepository.findByIdWithSourceWithoutAcl(
                            tenantId,
                            subflowId.namespace(),
                            subflowId.flowId(),
                            subflowId.revision()
                        );
                    } else {
                        flowById = flowRepository.findByIdWithSource(
                            tenantId,
                            subflowId.namespace(),
                            subflowId.flowId(),
                            subflowId.revision()
                        );
                    }

                    return flowById.orElseThrow(
                        () -> new NoSuchElementException(
                            "Unable to find subflow " +
                                (subflowId.revision().isEmpty() ? subflowId.flowUidWithoutRevision() : subflowId.flowUid())
                                + " for task " + task.getId()
                        )
                    );
                }
            );
            subflow = pluginDefaultService.injectAllDefaults(subflow, false);

            SubflowGraphTask finalSubflowGraphTask = subflowGraphTask;
            return new TaskToClusterReplacer(
                parentWithSubflowGraphTask.getKey(),
                subflowGraphTask,
                this.of(
                    new SubflowGraphCluster(subflowGraphTask.getUid(), subflowGraphTask),
                    subflow,
                    expandedSubflows.stream().filter(expandedSubflow -> expandedSubflow.startsWith(finalSubflowGraphTask.getUid() + ".")).toList(),
                    flowByUid
                )
            );
        }))
            .forEach(TaskToClusterReplacer::replace);

        return graphCluster;
    }

    /**
     * Builds the variable map used to resolve task properties for display.
     *
     * <p>When an execution is present, the full execution context is used so that
     * {@code inputs.*}, {@code outputs.*}, {@code trigger.*}, etc. are available.
     * Without an execution, only flow-level variables ({@code flow.*}, {@code vars.*},
     * {@code globals.*}) are included; runtime-only variables are simply absent, causing
     * their expression segments to fall back to raw text naturally.
     *
     * <p>The {@code envs} entry is always removed: environment variables must only ever be
     * surfaced through the masked {@code env()} function, never via direct {@code envs.*} map
     * access which would bypass that mask.
     */
    private Map<String, Object> buildDisplayVariables(FlowWithSource flow, Execution execution) {
        if (execution != null) {
            var variables = new HashMap<>(runContextFactory.of(flow, execution).getVariables());
            variables.remove("envs");
            return variables;
        }

        // Pre-exec: build a minimal context with only flow-level stable variables.
        // vars are not added automatically without an execution, so inject them explicitly.
        var logger = new RunContextLogger();
        var extraVars = flow.getVariables() != null
            ? Map.of("vars", (Object) flow.getVariables())
            : Map.<String, Object>of();
        var variables = new HashMap<>(new RunVariables.DefaultBuilder()
            .withFlow(flow)
            .withVariables(extraVars)
            .build(logger, PropertyContext.create(variableRenderer)));
        variables.remove("envs");
        return variables;
    }

    private record TaskToClusterReplacer(GraphCluster parentCluster, AbstractGraph taskToReplace,
        GraphCluster clusterForReplacement) {
        public void replace() {
            parentCluster.addNode(clusterForReplacement, false);
            parentCluster.getGraph().edges()
                .forEach(edge ->
                {
                    if (edge.getSource().equals(taskToReplace)) {
                        parentCluster.addEdge(clusterForReplacement.getEnd(), edge.getTarget(), edge.getValue());
                    } else if (edge.getTarget().equals(taskToReplace)) {
                        parentCluster.addEdge(edge.getSource(), clusterForReplacement.getRoot(), edge.getValue());
                    }
                });
            parentCluster.getGraph().removeNode(taskToReplace);

            if (taskToReplace.getBranchType() != null) {
                clusterForReplacement.updateWithChildren(taskToReplace.getBranchType());
            }
        }
    }
}
