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
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
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
    @Inject
    private FlowRepositoryInterface flowRepository;
    @Inject
    private TriggerRepositoryInterface triggerRepository;
    @Inject
    private FlowParsingService flowParsingService;
    @Inject
    private RunContextFactory runContextFactory;

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
        flow = flowParsingService.parse(flow, false);
        List<TriggerState> triggers = null;
        if (flow.getTriggers() != null) {
            triggers = triggerRepository.find(Pageable.UNPAGED, null, tenantId, flow.getNamespace(), flow.getId(), null);
        }
        GraphCluster graphCluster = GraphUtils.of(baseGraph, flow, execution, triggers);
        this.replaceCollapsedDisabledSubflows(graphCluster, flow, flowByUid, execution, expandedSubflows);

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
            SubflowGraphTask original = parentWithSubflowGraphTask.getValue();
            Task task = (Task) original.getTask();
            RunContext runContext = PebbleUtil.containsOpeningBlockDelimiter(original.executableTask().subflowId().flowUid()) && execution != null
                ? runContextFactory.of(finalFlow, task, execution, original.getTaskRun())
                : null;

            ExecutableTask.SubflowId subflowId = original.withRenderedSubflowId(runContext).executableTask().subflowId();

            if (PebbleUtil.containsOpeningBlockDelimiter(subflowId.flowUid())) {
                throw new IllegalArgumentException(
                    "Can't expand subflow task '" + task.getId() + "' because namespace and/or flowId contains dynamic values. This can only be viewed on an execution."
                );
            }

            FlowWithSource subflow = this.findSubflow(tenantId, subflowId, flowByUid, execution)
                .orElseThrow(
                    () -> new NoSuchElementException(
                        "Unable to find subflow " +
                            (subflowId.revision().isEmpty() ? subflowId.flowUidWithoutRevision() : subflowId.flowUid())
                            + " for task " + task.getId()
                    )
                );
            SubflowGraphTask subflowGraphTask = original.withRenderedSubflowId(runContext, subflow.isDisabled());
            subflow = flowParsingService.parse(subflow, false);

            SubflowGraphTask finalSubflowGraphTask = subflowGraphTask;
            return new TaskToClusterReplacer(
                parentWithSubflowGraphTask.getKey(),
                original,
                this.of(
                    new SubflowGraphCluster(subflowGraphTask.getUid(), subflowGraphTask),
                    subflow,
                    expandedSubflows.stream().filter(expandedSubflow -> expandedSubflow.startsWith(finalSubflowGraphTask.getUid() + ".")).toList(),
                    flowByUid,
                    execution
                )
            );
        }))
            .forEach(TaskToClusterReplacer::replace);

        return graphCluster;
    }

    private void replaceCollapsedDisabledSubflows(
        GraphCluster graphCluster,
        FlowWithSource flow,
        Map<String, FlowWithSource> flowByUid,
        Execution execution,
        List<String> expandedSubflows
    ) {
        String tenantId = flow.getTenantId();
        List<Map.Entry<GraphCluster, SubflowGraphTask>> collapsed = graphCluster.allNodesByParent().entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
                .filter(SubflowGraphTask.class::isInstance)
                .map(SubflowGraphTask.class::cast)
                .filter(node -> !expandedSubflows.contains(node.getUid()))
                .map(node -> Map.entry(entry.getKey(), node)))
            .toList();

        for (Map.Entry<GraphCluster, SubflowGraphTask> entry : collapsed) {
            SubflowGraphTask original = entry.getValue();
            if (original.executableTask() == null) {
                continue;
            }

            ExecutableTask.SubflowId subflowId = original.executableTask().subflowId();
            if (PebbleUtil.containsOpeningBlockDelimiter(subflowId.flowUid())) {
                continue;
            }

            boolean disabled = this.findSubflow(tenantId, subflowId, flowByUid, execution)
                .map(FlowWithSource::isDisabled)
                .orElse(false);
            if (disabled) {
                replaceNode(entry.getKey(), original, original.withDisabled(true));
            }
        }
    }

    private static void replaceNode(GraphCluster parent, SubflowGraphTask original, SubflowGraphTask replacement) {
        List<Graph.Edge<AbstractGraph, Relation>> edges = new ArrayList<>(parent.getGraph().edges());
        parent.getGraph().removeNode(original);
        parent.addNode(replacement, false);
        for (Graph.Edge<AbstractGraph, Relation> edge : edges) {
            AbstractGraph source = edge.getSource().equals(original) ? replacement : edge.getSource();
            AbstractGraph target = edge.getTarget().equals(original) ? replacement : edge.getTarget();
            if (source.equals(replacement) || target.equals(replacement)) {
                parent.addEdge(source, target, edge.getValue());
            }
        }
    }

    private Optional<FlowWithSource> findSubflow(
        String tenantId,
        ExecutableTask.SubflowId subflowId,
        Map<String, FlowWithSource> flowByUid,
        Execution execution
    ) {
        String key = subflowId.flowUid();
        if (flowByUid.containsKey(key)) {
            return Optional.ofNullable(flowByUid.get(key));
        }

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
        flowByUid.put(key, flowById.orElse(null));
        return flowById;
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
