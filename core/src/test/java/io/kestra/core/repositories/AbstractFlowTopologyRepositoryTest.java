package io.kestra.core.repositories;

import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
public abstract class AbstractFlowTopologyRepositoryTest {
    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @Test
    void suite() {
        flowTopologyRepository.save(FlowTopology.builder()
            .relation(FlowRelation.FLOW_TASK)
            .source(FlowNode.builder()
                .namespace("io.kestra.test")
                .id("flow-a")
                .build()
            )
            .destination(FlowNode.builder()
                .namespace("io.kestra.test")
                .id("flow-b")
                .build()
            )
            .build()
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow("io.kestra.test", "flow-a", false);

        assertThat(list.size(), is(1));
    }
}
