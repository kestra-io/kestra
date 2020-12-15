package org.kestra.core.models.hierarchies;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.services.GraphService;

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
            .clusters(clusters(graph)
                .stream()
                .map(g -> new Cluster(g, g.getGraph()
                    .nodes()
                    .stream()
                    .map(AbstractGraphTask::getUid)
                    .collect(Collectors.toList()))
                )
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

    private static List<GraphCluster> clusters(GraphCluster graphCluster) {
        return graphCluster.getGraph().nodes()
            .stream()
            .flatMap(t -> t instanceof GraphCluster ? Stream.concat(Stream.of((GraphCluster) t), clusters((GraphCluster) t).stream()) : Stream.of())
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
    }
}
