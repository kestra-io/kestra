package io.kestra.scheduler.vnodes;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.scheduler.vnodes.VNodeConsistentHashRing;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class VNodeConsistentHashRingTest {
    private VNodeConsistentHashRing ring;

    @BeforeEach
    void setUp() {
        ring = VNodeConsistentHashRing.of(8);
    }

    @Test
    void shouldAddSingleNode() {
        // Given
        String nodeId = "node1";

        // When
        ring.addNode(nodeId);

        // Then
        Map<String, Set<Integer>> assignments = ring.assignVNodes();
        assertThat(assignments).containsKey(nodeId);
        assertThat(assignments.get(nodeId)).isNotEmpty();
    }

    @Test
    void shouldAddMultipleNodes() {
        // Given
        Set<String> nodes = Set.of("node1", "node2", "node3");

        // When
        ring.addNodes(nodes);

        // Then
        Map<String, Set<Integer>> assignments = ring.assignVNodes();
        assertThat(assignments.keySet()).containsAll(nodes);
        assignments.values().forEach(vnodes -> assertThat(vnodes).isNotEmpty());
    }

    @Test
    void shouldRemoveNode() {
        // Given
        String nodeId = "node1";
        ring.addNode(nodeId);
        ring.addNode("node2");

        // When
        ring.removeNode(nodeId);

        // Then
        Map<String, Set<Integer>> assignments = ring.assignVNodes();
        assertThat(assignments).doesNotContainKey(nodeId);
        assertThat(assignments).containsKey("node2");
    }

    @Test
    void shouldAssignAllVnodes() {
        // Given
        ring.addNodes(Set.of("node1", "node2"));

        // When
        Map<String, Set<Integer>> assignments = ring.assignVNodes();

        // Then
        int totalVnodes = assignments.values().stream().mapToInt(Set::size).sum();
        assertThat(totalVnodes).isEqualTo(8); // matches the vnodeCount set in setUp
    }

    @Test
    void shouldRebalanceAfterNodeRemoval() {
        // Given
        ring.addNodes(Set.of("node1", "node2", "node3"));
        Map<String, Set<Integer>> beforeRemoval = ring.assignVNodes();

        // When
        ring.removeNode("node2");
        Map<String, Set<Integer>> afterRemoval = ring.assignVNodes();

        // Then
        assertThat(afterRemoval).doesNotContainKey("node2");
        int totalVnodes = afterRemoval.values().stream().mapToInt(Set::size).sum();
        assertThat(totalVnodes).isEqualTo(8); // all vnodes are still assigned
        assertThat(beforeRemoval).isNotEqualTo(afterRemoval); // assignments changed
    }

    @Test
    void shouldReturnEmptyAssignmentWhenNoNodeIsAdded() {
        // Given
        // When
        Map<String, Set<Integer>> assignments = ring.assignVNodes();
        // Then
        assertThat(assignments).isEmpty();
    }
}