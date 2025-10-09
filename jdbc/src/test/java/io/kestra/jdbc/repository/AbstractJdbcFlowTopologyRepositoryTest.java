package io.kestra.jdbc.repository;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.AbstractFlowTopologyRepositoryTest;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcFlowTopologyRepositoryTest extends AbstractFlowTopologyRepositoryTest {
    @Inject
    private AbstractJdbcFlowTopologyRepository flowTopologyRepository;

    @Test
    void saveMultiple() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = FlowWithSource.builder()
            .tenantId(tenant)
            .id("flow-a")
            .namespace("io.kestra.tests")
            .revision(1)
            .build();

        flowTopologyRepository.save(
            flow,
            List.of(
                createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
            )
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(tenant, "io.kestra.tests", "flow-a", false);
        assertThat(list.size()).isEqualTo(1);

        flowTopologyRepository.save(
            flow,
            List.of(
                createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests"),
                createSimpleFlowTopology(tenant, "flow-a", "flow-c", "io.kestra.tests")
            )
        );

        list = flowTopologyRepository.findByNamespace(tenant, "io.kestra.tests");

        assertThat(list.size()).isEqualTo(2);
    }
}