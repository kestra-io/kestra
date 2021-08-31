package io.kestra.core.models.hierarchies;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.GraphService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
@Builder
public class FlowGraph {
    List<AbstractGraphTask> nodes;
    List<Edge> edges;
    List<Cluster> clusters;

    public static FlowGraph of(Flow flow) throws IllegalVariableEvaluationException {
        return FlowGraph.of(flow, null);
    }

    public static FlowGraph of(Flow flow, Execution execution) throws IllegalVariableEvaluationException {
        GraphCluster graph = GraphService.of(flow, execution);

        return FlowGraph.builder()
            .nodes(GraphService.nodes(graph))
            .edges(GraphService.edges(graph))
            .clusters(GraphService.clusters(graph, new ArrayList<>())
                .stream()
                .map(g -> new Cluster(g.getKey(), g.getKey().getGraph()
                    .nodes()
                    .stream()
                    .map(AbstractGraphTask::getUid)
                    .collect(Collectors.toList()),
                    g.getValue()
                ))
                .collect(Collectors.toList())
            )
            .build();
    }

    @Getter
    @AllArgsConstructor
    public static class Edge {
        private final String source;
        private final String target;
        private final Relation relation;
    }

    @Getter
    @AllArgsConstructor
    public static class Cluster {
        private final GraphCluster cluster;
        private final List<String> nodes;
        private final List<String> parents;
    }
}
