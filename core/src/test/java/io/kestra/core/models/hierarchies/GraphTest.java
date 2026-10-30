package io.kestra.core.models.hierarchies;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphTest {
    // no equals/hashCode, like AbstractGraph, so hash based ordering would be random
    private record Node(String id) {
        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    @Test
    void edgesKeepInsertionOrder() {
        Graph<Node, String> graph = new Graph<>();
        Node parent = new Node("parent");
        graph.addNode(parent);

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Node child = new Node("t" + i);
            graph.addNode(child);
            graph.addEdge(parent, child, "t" + i);
            expected.add("t" + i);
        }

        assertThat(graph.successors(parent).stream().map(Node::id).toList()).isEqualTo(expected);
        assertThat(graph.edges().stream().map(e -> e.getTarget().id()).toList()).isEqualTo(expected);
    }
}
