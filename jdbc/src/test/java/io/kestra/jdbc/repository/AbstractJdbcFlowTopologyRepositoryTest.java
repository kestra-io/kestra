package io.kestra.jdbc.repository;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.AbstractFlowTopologyRepositoryTest;
import io.kestra.core.tenant.TenantService;
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
                createSimpleFlowTopology("flow-a", "flow-b", "io.kestra.tests")
            )
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(TenantService.MAIN_TENANT, "io.kestra.tests", "flow-a", false);
        assertThat(list.size()).isEqualTo(1);

        flowTopologyRepository.save(
            flow,
            List.of(
                createSimpleFlowTopology("flow-a", "flow-b", "io.kestra.tests"),
                createSimpleFlowTopology("flow-a", "flow-c", "io.kestra.tests")
            )
        );

        list = flowTopologyRepository.findByNamespace(TenantService.MAIN_TENANT, "io.kestra.tests");

        assertThat(list.size()).isEqualTo(2);
    }



    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}