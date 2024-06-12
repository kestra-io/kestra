package io.kestra.core.repositories;

import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public abstract class AbstractFlowTopologyRepositoryTest {
    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    protected FlowTopology createSimpleFlowTopology(String flowA, String flowB) {
        return FlowTopology.builder()
            .relation(FlowRelation.FLOW_TASK)
            .source(FlowNode.builder()
                .id(flowA)
                .namespace("io.kestra.tests")
                .uid(flowA)
                .build()
            )
            .destination(FlowNode.builder()
                .id(flowB)
                .namespace("io.kestra.tests")
                .uid(flowB)
                .build()
            )
            .build();
    }

    @Test
    void suite() {
        flowTopologyRepository.save(
            createSimpleFlowTopology("flow-a", "flow-b")
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(null, "io.kestra.tests", "flow-a", false);

        assertThat(list.size(), is(1));
    }
}
