package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.SubflowGraphCluster;
import io.kestra.core.models.hierarchies.SubflowGraphTask;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.Rethrow;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Singleton
@Slf4j
public class GraphService {
    @Inject
    private FlowRepositoryInterface flowRepository;

    public FlowGraph flowGraph(Flow flow, List<String> expandedSubflows) throws IllegalVariableEvaluationException {
        return FlowGraph.of(this.of(flow, Optional.ofNullable(expandedSubflows).orElse(Collections.emptyList()), new HashMap<>()));
    }

    public GraphCluster of(Flow flow, List<String> expandedSubflows, Map<String, Flow> flowByUid) throws IllegalVariableEvaluationException {
        return this.of(null, flow, expandedSubflows, flowByUid);
    }

    public GraphCluster of(GraphCluster baseGraph, Flow flow, List<String> expandedSubflows, Map<String, Flow> flowByUid) throws IllegalVariableEvaluationException {
        GraphCluster graphCluster = GraphUtils.of(baseGraph, flow, null);

        List<SubflowGraphTask> subflowsToReplace = graphCluster.getGraph().nodes().stream()
            .filter(node -> node instanceof SubflowGraphTask)
            .map(SubflowGraphTask.class::cast)
            .filter(subflowGraphTask -> expandedSubflows.contains(subflowGraphTask.getUid()))
            .toList();

        subflowsToReplace.stream()
            .map(Rethrow.throwFunction(subflowGraphTask -> {
                Flow subflow = flowByUid.computeIfAbsent(
                    subflowGraphTask.getTask().flowUid(),
                    uid -> flowRepository.findById(
                        subflowGraphTask.getTask().getNamespace(),
                        subflowGraphTask.getTask().getFlowId(),
                        Optional.ofNullable(subflowGraphTask.getTask().getRevision())
                    ).orElse(null)
                );

                if (subflow == null) {
                    throw new IllegalArgumentException("Unable to find subflow " + subflowGraphTask.getTask().flowUid() + " for task " + subflowGraphTask.getTask().getId());
                }

                return Map.entry(
                    subflowGraphTask,
                    this.of(
                        new SubflowGraphCluster(subflowGraphTask.getUid(), subflowGraphTask),
                        subflow,
                        expandedSubflows.stream().filter(expandedSubflow -> expandedSubflow.startsWith(subflowGraphTask.getUid() + ".")).toList(),
                        flowByUid
                    )
                );
            }))
            .forEach(nodeToReplaceWithCluster -> {
                graphCluster.addNode(nodeToReplaceWithCluster.getValue(), false);
                graphCluster.getGraph().edges()
                    .forEach(edge -> {
                        if (edge.getSource().equals(nodeToReplaceWithCluster.getKey())) {
                            graphCluster.getGraph().addEdge(nodeToReplaceWithCluster.getValue().getEnd(), edge.getTarget(), edge.getValue());
                        } else if (edge.getTarget().equals(nodeToReplaceWithCluster.getKey())) {
                            graphCluster.getGraph().addEdge(edge.getSource(), nodeToReplaceWithCluster.getValue().getRoot(), edge.getValue());
                        }
                    });
                graphCluster.getGraph().removeNode(nodeToReplaceWithCluster.getKey());
            });

        return graphCluster;
    }
}
