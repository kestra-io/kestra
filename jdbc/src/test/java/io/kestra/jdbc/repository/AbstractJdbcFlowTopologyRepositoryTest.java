package io.kestra.jdbc.repository;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.AbstractFlowTopologyRepositoryTest;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcFlowTopologyRepositoryTest extends AbstractFlowTopologyRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    private AbstractJdbcFlowTopologyRepository flowTopologyRepository;

    @Test
    void saveMultiple() {
        FlowWithSource flow = FlowWithSource.builder()
            .id("flow-a")
            .namespace("io.kestra.tests")
            .revision(1)
            .build();

        flowTopologyRepository.save(
            flow,
            List.of(
                createSimpleFlowTopology("flow-a", "flow-b")
            )
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(null, "io.kestra.tests", "flow-a", false);
        assertThat(list.size()).isEqualTo(1);

        flowTopologyRepository.save(
            flow,
            List.of(
                createSimpleFlowTopology("flow-a", "flow-c")
            )
        );

        list = flowTopologyRepository.findByFlow(null, "io.kestra.tests", "flow-a", false);

        assertThat(list.size()).isEqualTo(1);
        assertThat(list.getFirst().getDestination().getId()).isEqualTo("flow-c");

        flowTopologyRepository.save(
            flow,
            List.of(
                createSimpleFlowTopology("flow-a", "flow-b"),
                createSimpleFlowTopology("flow-a", "flow-c")
            )
        );

        list = flowTopologyRepository.findByNamespace(null, "io.kestra.tests");

        assertThat(list.size()).isEqualTo(2);
    }



    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}