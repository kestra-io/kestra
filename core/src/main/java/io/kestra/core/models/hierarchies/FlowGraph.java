package io.kestra.core.models.hierarchies;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.GraphService;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Value
@Builder
public class FlowGraph {
    List<AbstractGraph> nodes;
    List<Edge> edges;
    List<Cluster> clusters;
    List<String> flowables;

    public static FlowGraph of(Flow flow) throws IllegalVariableEvaluationException {
        return FlowGraph.of(flow, null);
    }

    public static FlowGraph of(Flow flow, Execution execution) throws IllegalVariableEvaluationException {
        GraphCluster graph = GraphService.of(flow, execution);

        return FlowGraph.builder()
            .flowables(GraphService.flowables(flow))
            .nodes(GraphService.nodes(graph))
            .edges(GraphService.edges(graph))
            .clusters(GraphService.clusters(graph, new ArrayList<>())
                .stream()
                .map(g -> new Cluster(g.getKey(), g.getKey().getGraph()
                    .nodes()
                    .stream()
                    .map(AbstractGraph::getUid)
                    .collect(Collectors.toList()),
                    g.getValue(),
                    g.getKey().getRoot().getUid(),
                    g.getKey().getEnd().getUid()
                ))
                .collect(Collectors.toList())
            )
            .build();
    }

    @Getter
    @AllArgsConstructor
    @ToString
    @EqualsAndHashCode
    public static class Edge {
        private final String source;
        private final String target;
        private final Relation relation;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    @EqualsAndHashCode
    public static class Cluster {
        private final AbstractGraph cluster;
        private final List<String> nodes;
        private final List<String> parents;
        private final String start;
        private final String end;
    }
}
