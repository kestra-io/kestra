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
import io.kestra.core.runners.DisplayExpressionRenderer;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.serializers.JacksonMapper;
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
    @Inject
    private FlowRepositoryInterface flowRepository;
    @Inject
    private TriggerRepositoryInterface triggerRepository;
    @Inject
    private PluginDefaultService pluginDefaultService;
    @Inject
    private RunContextFactory runContextFactory;
    @Inject
    private NamespaceVariablesProvider namespaceVariablesProvider;
    @Inject
    private DisplayExpressionRenderer displayExpressionRenderer;

    public FlowGraph flowGraph(FlowWithSource flow, List<String> expandedSubflows) throws IllegalVariableEvaluationException, FlowProcessingException {
        FlowGraph graph = this.flowGraph(flow, expandedSubflows, null);
        populateRenderedProperties(flow, graph);
        return graph;
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
     * Resolves top-level string task properties for display using namespace + flow variables.
     * Only called for the pre-execution (no-{@link Execution}) graph variant; per-execution graphs
     * already carry resolved values through the execution context.
     */
    private void populateRenderedProperties(FlowWithSource flow, FlowGraph graph) {
        Map<String, Object> nsVars = namespaceVariablesProvider.fetchVariables(flow.getTenantId(), flow.getNamespace());
        Map<String, Object> flowVars = flow.getVariables() != null ? flow.getVariables() : Map.of();
        if (nsVars.isEmpty() && flowVars.isEmpty()) {
            return;
        }
        // Flow-level variables take precedence over namespace variables.
        Map<String, Object> merged = new HashMap<>(nsVars);
        merged.putAll(flowVars);
        Map<String, Object> context = Map.of("vars", merged);

        for (AbstractGraph node : graph.getNodes()) {
            if (!(node instanceof AbstractGraphTask taskNode) || taskNode.getTask() == null) {
                continue;
            }
            Map<String, Object> taskMap = JacksonMapper.toMap(taskNode.getTask());
            Map<String, String> rendered = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : taskMap.entrySet()) {
                if (entry.getValue() instanceof String strVal) {
                    rendered.put(entry.getKey(), displayExpressionRenderer.resolveForDisplay(strVal, context));
                }
            }
            if (!rendered.isEmpty()) {
                taskNode.setRenderedProperties(rendered);
            }
        }
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
