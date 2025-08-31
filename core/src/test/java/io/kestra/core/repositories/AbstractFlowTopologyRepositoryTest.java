package io.kestra.core.repositories;

import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.tenant.TenantService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractFlowTopologyRepositoryTest {
    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    protected FlowTopology createSimpleFlowTopology(String flowA, String flowB, String namespace) {
        return FlowTopology.builder()
            .relation(FlowRelation.FLOW_TASK)
            .source(FlowNode.builder()
                .id(flowA)
                .namespace(namespace)
                .tenantId(TenantService.MAIN_TENANT)
                .uid(flowA)
                .build()
            )
            .destination(FlowNode.builder()
                .id(flowB)
                .namespace(namespace)
                .tenantId(TenantService.MAIN_TENANT)
                .uid(flowB)
                .build()
            )
            .build();
    }

    @Test
    void findByFlow() {
        flowTopologyRepository.save(
            createSimpleFlowTopology("flow-a", "flow-b", "io.kestra.tests")
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(TenantService.MAIN_TENANT, "io.kestra.tests", "flow-a", false);

        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void findByNamespace() {
        flowTopologyRepository.save(
            createSimpleFlowTopology("flow-a", "flow-b", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology("flow-c", "flow-d", "io.kestra.tests")
        );

        List<FlowTopology> list = flowTopologyRepository.findByNamespace(TenantService.MAIN_TENANT, "io.kestra.tests");

        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void findAll() {
        flowTopologyRepository.save(
            createSimpleFlowTopology("flow-a", "flow-b", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology("flow-c", "flow-d", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology("flow-e", "flow-f", "io.kestra.tests.2")
        );

        List<FlowTopology> list = flowTopologyRepository.findAll(TenantService.MAIN_TENANT);

        assertThat(list.size()).isEqualTo(3);
    }
}
