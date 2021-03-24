package io.kestra.core.services;

import io.kestra.core.models.hierarchies.*;

import java.net.URISyntaxException;

public class Graph2DotService {
    public static String dot(Graph<AbstractGraphTask, Relation> graph) throws URISyntaxException {
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

    private static String nodeAndEdges(Graph<AbstractGraphTask, Relation> graph, int level, String uid) {
        StringBuilder sb = new StringBuilder();

        for(AbstractGraphTask node : graph.nodes()) {
            if (node instanceof GraphCluster) {
                GraphCluster subGraph = (GraphCluster) node;

                if (uid == null || !uid.equals(subGraph.getUid())) {
                    sb.append(subgraph(subGraph, level + 1));
                }
            } else {
                sb.append(indent(level)).append("  ").append(node(node)).append(";\n");
            }
        }

        for(Graph.Edge<AbstractGraphTask, Relation> e : graph.edges()) {
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

    private static String node(AbstractGraphTask node) {
        return name(node) +  label(node);
    }

    private static String label(AbstractGraphTask node) {
        String shape = node instanceof GraphClusterRoot || node instanceof GraphClusterEnd ? "point" : "box";
        String label = node instanceof GraphClusterRoot ? "start" : (node instanceof GraphClusterEnd ? "end" :
            node.getUid() + (node.getTaskRun() != null ? " > " + node.getTaskRun().getValue() + " (" + node.getTaskRun().getId() + ")" : "")
        );

        return "[shape=" + shape + ",label=\"" + label + "\"]";
    }

    private static String nodeName(AbstractGraphTask node) {
        return "\"" + node.getUid() + "\"";
    }

    private static String name(AbstractGraphTask node) {
        return "\"" + node.getUid() + "\"";
    }
}
