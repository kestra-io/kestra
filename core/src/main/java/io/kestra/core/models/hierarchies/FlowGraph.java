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
        // Build base nodes, edges and clusters from the underlying graph
        var nodes = GraphUtils.nodes(graph);
        var edges = new ArrayList<Edge>(GraphUtils.edges(graph));

        var clusters = GraphUtils.clusters(graph, new ArrayList<>())
            .stream()
            .map(g -> new Cluster(
                g.getKey(),
                g.getKey().getGraph()
                    .nodes()
                    .stream()
                    .map(AbstractGraph::getUid)
                    .toList(),
                g.getValue(),
                g.getKey().getRoot().getUid(),
                g.getKey().getEnd().getUid()
            ))
            .toList();

        // Visualization-only enhancement: for LoopUntil/WaitFor clusters, add a back-edge from end to start
        // This makes cycles obvious in the UI without altering the runtime GraphCluster (avoids introducing cycles there).
        clusters.forEach(c -> {
            try {
                if (c.getCluster() instanceof GraphCluster cluster && cluster.getTaskNode() instanceof AbstractGraphTask taskNode) {
                    var task = taskNode.getTask();
                    if (task != null) {
                        String type = task.getType();
                        // Match on canonical type name; alias "WaitFor" maps to LoopUntil but we include it defensively
                        boolean isLoop = type != null && (type.endsWith(".LoopUntil") || type.endsWith(".WaitFor")
                            || type.contains("io.kestra.plugin.core.flow.LoopUntil") || type.contains("io.kestra.plugin.core.flow.WaitFor"));
                        if (isLoop && c.getStart() != null && c.getEnd() != null) {
                            edges.add(new Edge(
                                c.getEnd(),
                                c.getStart(),
                                new Relation(null, null) // no specific relation type; renderer will still show the arrow
                            ));
                        }
                    }
                }
            } catch (Exception ignored) {
                // Defensive: never break graph generation due to visualization-only enhancement
            }
        });

        return FlowGraph.builder()
            .nodes(nodes)
            .edges(edges)
            .clusters(clusters)
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
                .toList()
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
