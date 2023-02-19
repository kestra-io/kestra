package io.kestra.jdbc.repository;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.AbstractFlowTopologyRepositoryTest;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public abstract class AbstractJdbcFlowTopologyRepositoryTest  extends AbstractFlowTopologyRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    private AbstractJdbcFlowTopologyRepository flowTopologyRepository;

    @Test
    void saveMultiple() {
        Flow flow = Flow.builder()
            .id("flow-a")
            .namespace("io.kestra.tests")
            .revision(1)
            .build();

        flowTopologyRepository.save(
            flow,
            List.of(FlowTopology.builder()
                .relation(FlowRelation.FLOW_TASK)
                .source(FlowNode.builder()
                    .id("flow-a")
                    .namespace("io.kestra.tests")
                    .build()
                )
                .destination(FlowNode.builder()
                    .id("flow-b")
                    .namespace("io.kestra.tests")
                    .build()
                )
                .build()
            )
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow("io.kestra.tests", "flow-a", false);
        assertThat(list.size(), is(1));

        flowTopologyRepository.save(
            flow,
            List.of(FlowTopology.builder()
                .relation(FlowRelation.FLOW_TASK)
                .source(FlowNode.builder()
                    .id("flow-a")
                    .namespace("io.kestra.tests")
                    .build()
                )
                .destination(FlowNode.builder()
                    .id("flow-c")
                    .namespace("io.kestra.tests")
                    .build()
                )
                .build()
            )
        );

        list = flowTopologyRepository.findByFlow("io.kestra.tests", "flow-a", false);

        assertThat(list.size(), is(1));
        assertThat(list.get(0).getDestination().getId(), is("flow-c"));
    }

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}