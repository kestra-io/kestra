package io.kestra.scheduler.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.kestra.core.scheduler.vnodes.internals.DefaultVNodesAssigner;

/**
 * Simple listener implementation for testing.
 */
public class TestVNodeAssignmentListener implements DefaultVNodesAssigner.VNodeAssignmentListener {
    final List<Set<Integer>> assigned = new ArrayList<>();
    int revokedCount = 0;

    @Override
    public void onVNodesAssigned(Set<Integer> vNodes) {
        assigned.add(vNodes);
    }

    @Override
    public void onVNodesRevoked() {
        revokedCount++;
    }

    public List<Set<Integer>> assigned() {
        return assigned;
    }

    public int revokedCount() {
        return revokedCount;
    }
}
