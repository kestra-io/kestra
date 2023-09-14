package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.*;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.Rethrow;
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
    private TaskDefaultService taskDefaultService;

    public FlowGraph flowGraph(Flow flow, List<String> expandedSubflows) throws IllegalVariableEvaluationException {
        return FlowGraph.of(this.of(flow, Optional.ofNullable(expandedSubflows).orElse(Collections.emptyList()), new HashMap<>()));
    }

    public GraphCluster of(Flow flow, List<String> expandedSubflows, Map<String, Flow> flowByUid) throws IllegalVariableEvaluationException {
        return this.of(null, flow, expandedSubflows, flowByUid);
    }

    public GraphCluster of(GraphCluster baseGraph, Flow flow, List<String> expandedSubflows, Map<String, Flow> flowByUid) throws IllegalVariableEvaluationException {
        flow = taskDefaultService.injectDefaults(flow);
        GraphCluster graphCluster = GraphUtils.of(baseGraph, flow, null);


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
                    subflowGraphTask.getTask().flowUid(),
                    uid -> flowRepository.findById(
                        subflowGraphTask.getTask().getNamespace(),
                        subflowGraphTask.getTask().getFlowId(),
                        Optional.ofNullable(subflowGraphTask.getTask().getRevision())
                    ).orElseThrow(() -> new NoSuchElementException(
                        "Unable to find subflow " +
                            (subflowGraphTask.getTask().getRevision() == null ? subflowGraphTask.getTask().flowUidWithoutRevision() : subflowGraphTask.getTask().flowUid())
                            + " for task " + subflowGraphTask.getTask().getId()
                    ))
                );
                subflow = taskDefaultService.injectDefaults(subflow);

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
