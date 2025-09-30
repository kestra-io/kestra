package io.kestra.scheduler.vnodes;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.scheduler.vnodes.VNodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class VNodesTest {
    
    @Test
    void shouldGetConsistentHashing() {
        int hash1 = VNodes.hash("random-string");
        int hash2 = VNodes.hash("random-string");
        assertThat(hash1).isEqualTo(hash2);
    }
    
    @Test
    void shouldComputeSameHashForBothTriggerAndFlow() {
        int vNode1 = VNodes.computeVNodeFromTrigger(TriggerId.of("tenant", "namespace", "flow", "trigger"), 16);
        int vNode2 = VNodes.computeVNodeFromFlow(FlowId.of("tenant", "namespace", "flow", null), 16);
        
        assertThat(vNode1).isEqualTo(vNode2);
    }
}