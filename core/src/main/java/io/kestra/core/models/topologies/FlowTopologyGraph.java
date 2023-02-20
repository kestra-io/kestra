package io.kestra.core.models.topologies;

import io.kestra.core.models.hierarchies.Graph;
import lombok.*;

import java.util.Set;
import java.util.stream.Collectors;

@Value
@Builder
public class FlowTopologyGraph {
    Set<FlowNode> nodes;
    Set<Edge> edges;

    public static FlowTopologyGraph of(Graph<FlowNode, FlowRelation> graph) {
        return FlowTopologyGraph.builder()
            .nodes(graph.nodes())
            .edges(graph.edges()
                .stream()
                .map(flowRelationEdge -> new Edge(
                    flowRelationEdge.getSource().getUid(),
                    flowRelationEdge.getTarget().getUid(),
                    flowRelationEdge.getValue()
                ))
                .collect(Collectors.toSet())
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
        private final FlowRelation relation;
    }
}
