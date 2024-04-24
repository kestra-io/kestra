package io.kestra.core.models.hierarchies;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.utils.GraphUtils;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Value
@Builder(toBuilder = true)
public class FlowGraph {
    List<AbstractGraph> nodes;
    List<Edge> edges;
    List<Cluster> clusters;
    List<String> flowables;

    public static FlowGraph of(GraphCluster graph) throws IllegalVariableEvaluationException {
        return FlowGraph.builder()
            .nodes(GraphUtils.nodes(graph))
            .edges(GraphUtils.edges(graph))
            .clusters(GraphUtils.clusters(graph, new ArrayList<>())
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

    /**
     * This method is used to clean the graph for informations
     * people with only EXECUTION - READ permission should not have access to.
     */
    public FlowGraph forExecution() {
        return this.toBuilder()
            .nodes(this.nodes
                .stream()
                .map(AbstractGraph::forExecution)
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
