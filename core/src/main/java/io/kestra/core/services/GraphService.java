package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.*;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.Rethrow;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Stream;

@Singleton
@Slf4j
public class GraphService {
    @Inject
    private FlowRepositoryInterface flowRepository;
    @Inject
    private TriggerRepositoryInterface triggerRepository;
    @Inject
    private PluginDefaultService pluginDefaultService;

    public FlowGraph flowGraph(Flow flow, List<String> expandedSubflows) throws IllegalVariableEvaluationException {
        return this.flowGraph(flow, expandedSubflows, null);
    }

    public FlowGraph flowGraph(Flow flow, List<String> expandedSubflows, Execution execution) throws IllegalVariableEvaluationException {
        return FlowGraph.of(this.of(flow, Optional.ofNullable(expandedSubflows).orElse(Collections.emptyList()), new HashMap<>(), execution));
    }

    public FlowGraph executionGraph(Flow flow, List<String> expandedSubflows, Execution execution) throws IllegalVariableEvaluationException {
        return FlowGraph.of(this.of(flow, Optional.ofNullable(expandedSubflows).orElse(Collections.emptyList()), new HashMap<>(), execution));
    }

    public GraphCluster of(Flow flow, List<String> expandedSubflows, Map<String, Flow> flowByUid) throws IllegalVariableEvaluationException {
        return this.of(flow, expandedSubflows, flowByUid, null);
    }

    public GraphCluster of(Flow flow, List<String> expandedSubflows, Map<String, Flow> flowByUid, Execution execution) throws IllegalVariableEvaluationException {
        return this.of(null, flow, expandedSubflows, flowByUid, execution);
    }

    public GraphCluster of(GraphCluster baseGraph, Flow flow, List<String> expandedSubflows, Map<String, Flow> flowByUid) throws IllegalVariableEvaluationException {
        return this.of(baseGraph, flow, expandedSubflows, flowByUid, null);
    }

    public GraphCluster of(GraphCluster baseGraph, Flow flow, List<String> expandedSubflows, Map<String, Flow> flowByUid, Execution execution) throws IllegalVariableEvaluationException {
        String tenantId = flow.getTenantId();
        flow = pluginDefaultService.injectDefaults(flow);
        List<Trigger> triggers = null;
        if (flow.getTriggers() != null) {
            triggers = triggerRepository.find(Pageable.UNPAGED, null, tenantId, flow.getNamespace(), flow.getId());
        }
        GraphCluster graphCluster = GraphUtils.of(baseGraph, flow, execution, triggers);


        Stream<Map.Entry<GraphCluster, SubflowGraphTask>> subflowToReplaceByParent = graphCluster.allNodesByParent().entrySet().stream()
            .flatMap(entry -> {
                List<SubflowGraphTask> subflowGraphTasks = entry.getValue().stream()
                    .filter(node -> node instanceof SubflowGraphTask && expandedSubflows.contains(node.getUid()))
                    .map(SubflowGraphTask.class::cast)
                    .toList();

                if (subflowGraphTasks.isEmpty()) {
                    return Stream.empty();
                }

                return subflowGraphTasks.stream().map(subflowGraphTask -> Map.entry(entry.getKey(), subflowGraphTask));
            });

        subflowToReplaceByParent.map(Rethrow.throwFunction(parentWithSubflowGraphTask -> {
                SubflowGraphTask subflowGraphTask = parentWithSubflowGraphTask.getValue();
                Flow subflow = flowByUid.computeIfAbsent(
                    subflowGraphTask.getExecutableTask().subflowId().flowUid(),
                    uid -> flowRepository.findByIdWithoutAcl(
                        tenantId,
                        subflowGraphTask.getExecutableTask().subflowId().namespace(),
                        subflowGraphTask.getExecutableTask().subflowId().flowId(),
                        subflowGraphTask.getExecutableTask().subflowId().revision()
                    ).orElseThrow(() -> new NoSuchElementException(
                        "Unable to find subflow " +
                            (subflowGraphTask.getExecutableTask().subflowId().revision().isEmpty() ? subflowGraphTask.getExecutableTask().subflowId().flowUidWithoutRevision() : subflowGraphTask.getExecutableTask().subflowId().flowUid())
                            + " for task " + subflowGraphTask.getTask().getId()
                    ))
                );
                subflow = pluginDefaultService.injectDefaults(subflow);

                return new TaskToClusterReplacer(
                    parentWithSubflowGraphTask.getKey(),
                    subflowGraphTask,
                    this.of(
                        new SubflowGraphCluster(subflowGraphTask.getUid(), subflowGraphTask),
                        subflow,
                        expandedSubflows.stream().filter(expandedSubflow -> expandedSubflow.startsWith(subflowGraphTask.getUid() + ".")).toList(),
                        flowByUid
                    )
                );
            }))
            .forEach(TaskToClusterReplacer::replace);

        return graphCluster;
    }

    private record TaskToClusterReplacer(GraphCluster parentCluster, AbstractGraph taskToReplace,
                                         GraphCluster clusterForReplacement) {
        public void replace() {
            parentCluster.addNode(clusterForReplacement, false);
            parentCluster.getGraph().edges()
                .forEach(edge -> {
                    if (edge.getSource().equals(taskToReplace)) {
                        parentCluster.addEdge(clusterForReplacement.getEnd(), edge.getTarget(), edge.getValue());
                    } else if (edge.getTarget().equals(taskToReplace)) {
                        parentCluster.addEdge(edge.getSource(), clusterForReplacement.getRoot(), edge.getValue());
                    }
                });
            parentCluster.getGraph().removeNode(taskToReplace);

            if (taskToReplace.isError()) {
                clusterForReplacement.updateErrorWithChildren(true);
            }
        }
    }
}
