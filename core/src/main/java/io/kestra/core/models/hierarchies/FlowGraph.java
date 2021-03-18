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
        GraphCluster graph = new GraphCluster();

        GraphService.sequential(
            graph,
            flow.getTasks(),
            flow.getErrors(),
            null,
            execution
        );

        return FlowGraph.builder()
            .nodes(nodes(graph))
            .edges(edges(graph))
            .clusters(clusters(graph, new ArrayList<>())
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

    private static List<AbstractGraphTask> nodes(GraphCluster graphCluster) {
        return graphCluster.getGraph().nodes()
            .stream()
            .flatMap(t -> t instanceof GraphCluster ? nodes((GraphCluster) t).stream() : Stream.of(t))
            .distinct()
            .collect(Collectors.toList());
    }

    private static List<Edge> edges(GraphCluster graphCluster) {
        return Stream.concat(
            graphCluster.getGraph().edges()
                .stream()
                .map(r -> new Edge(r.getSource().getUid(), r.getTarget().getUid(), r.getValue())),
            graphCluster.getGraph().nodes()
                .stream()
                .flatMap(t -> t instanceof GraphCluster ? edges((GraphCluster) t).stream() : Stream.of())
        )
            .collect(Collectors.toList());
    }

    private static List<Pair<GraphCluster, List<String>>> clusters(GraphCluster graphCluster, List<String> parents) {
        return graphCluster.getGraph().nodes()
            .stream()
            .flatMap(t -> {

                if (t instanceof GraphCluster) {
                    ArrayList<String> currentParents = new ArrayList<>(parents);
                    currentParents.add(t.getUid());

                    return Stream.concat(
                        Stream.of(Pair.of((GraphCluster) t, parents)),
                        clusters((GraphCluster) t, currentParents).stream()
                    );
                }

                return Stream.of();
            })
            .collect(Collectors.toList());
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
