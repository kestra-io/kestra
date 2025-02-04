package io.kestra.core.services;

import io.kestra.core.models.hierarchies.*;

import java.net.URISyntaxException;

public class Graph2DotService {
    public static String dot(Graph<AbstractGraph, Relation> graph) throws URISyntaxException {
        StringBuilder sb = new StringBuilder();

        sb.append("digraph G {\n");
        sb.append("  layout=dot;\n\n");
        sb.append(nodeAndEdges(graph, 0, null));
        sb.append("}");

        return sb.toString();
    }

    private static String subgraph(GraphCluster subGraph, int level) {
        StringBuilder sb = new StringBuilder();

        sb.append(indent(level)).append("subgraph \"cluster_").append(subGraph.getUid()).append("\"{\n");
        sb.append(indent(level)).append("  label = ").append(name(subGraph)).append(";\n");

        sb.append(nodeAndEdges(subGraph.getGraph(), level, subGraph.getUid()));
        sb.append(indent(level)).append("}");
        sb.append("\n\n");

        return sb.toString();
    }

    private static String nodeAndEdges(Graph<AbstractGraph, Relation> graph, int level, String uid) {
        StringBuilder sb = new StringBuilder();

        for(AbstractGraph node : graph.nodes()) {
            if (node instanceof GraphCluster subGraph) {
                if (uid == null || !uid.equals(subGraph.getUid())) {
                    sb.append(subgraph(subGraph, level + 1));
                }
            } else {
                sb.append(indent(level)).append("  ").append(node(node)).append(";\n");
            }
        }

        for(Graph.Edge<AbstractGraph, Relation> e : graph.edges()) {
            sb.append(indent(level))
                .append("  ")
                .append(nodeName(e.getSource()))
                .append(" -> ")
                .append(nodeName(e.getTarget()))
                .append(" ")
                .append(";\n");
        }

        return sb.toString();
    }

    private static String indent(int level) {
        return " ".repeat(level * 2);
    }

    private static String node(AbstractGraph node) {
        return name(node) +  label(node);
    }

    private static String label(AbstractGraph node) {
        String shape;

        if (node instanceof GraphClusterRoot || node instanceof GraphClusterFinally || node instanceof GraphClusterEnd) {
            shape = "point";
        } else {
            shape = "box";
        }

        return "[shape=" + shape + "]";
    }

    private static String nodeName(AbstractGraph node) {
        return "\"" + node.getUid() + "\"";
    }

    private static String name(AbstractGraph node) {
        return "\"" + node.getUid() + "\"";
    }
}
