package io.kestra.core.repositories;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractFlowTopologyRepositoryTest {
    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    protected FlowTopology createSimpleFlowTopology(String tenantId, String flowA, String flowB, String namespace) {
        return FlowTopology.builder()
            .relation(FlowRelation.FLOW_TASK)
            .source(
                FlowNode.builder()
                    .id(flowA)
                    .namespace(namespace)
                    .tenantId(tenantId)
                    .uid(tenantId + flowA)
                    .build()
            )
            .destination(
                FlowNode.builder()
                    .id(flowB)
                    .namespace(namespace)
                    .tenantId(tenantId)
                    .uid(tenantId + flowB)
                    .build()
            )
            .build();
    }

    @Test
    void findByFlow() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(tenant, "io.kestra.tests", "flow-a", false);

        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void findByNamespacePrefix() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );

        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-x", "flow-y", "io.kestra.tests.sub")
        );

        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-p", "flow-q", "io.other.namespace")
        );

        List<FlowTopology> list = flowTopologyRepository.findByNamespacePrefix(tenant, "io.kestra.tests");

        assertThat(list)
            .extracting(ft -> ft.getSource().getNamespace())
            .contains("io.kestra.tests", "io.kestra.tests.sub")
            .doesNotContain("io.other.namespace");

        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void findByNamespace() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-c", "flow-d", "io.kestra.tests")
        );

        List<FlowTopology> list = flowTopologyRepository.findByNamespace(tenant, "io.kestra.tests");

        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void findAll() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-c", "flow-d", "io.kestra.tests")
        );
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-e", "flow-f", "io.kestra.tests.2")
        );

        List<FlowTopology> list = flowTopologyRepository.findAll(tenant);

        assertThat(list.size()).isEqualTo(3);
    }

    @Test
    void findByFlowDestinationOnly() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );

        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-c", "flow-a", "io.kestra.tests")
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(tenant, "io.kestra.tests", "flow-a", true);

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getDestination().getId()).isEqualTo("flow-a");
        assertThat(list.getFirst().getSource().getId()).isEqualTo("flow-c");
    }

    @Test
    void findByFlowNoMatch() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        flowTopologyRepository.save(
            createSimpleFlowTopology(tenant, "flow-a", "flow-b", "io.kestra.tests")
        );

        List<FlowTopology> list = flowTopologyRepository.findByFlow(tenant, "io.kestra.tests", "flow-c", false);

        assertThat(list).isEmpty();
    }

    @Test
    void shouldReplaceEdgesWhenSavingSameFlowAgain() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.tests";
        Flow flowA = Flow.builder().id("flow-a").namespace(namespace).tenantId(tenant).build();

        flowTopologyRepository.save(flowA, List.of(createSimpleFlowTopology(tenant, "flow-a", "flow-b", namespace)));

        assertThat(flowTopologyRepository.findByFlow(tenant, namespace, "flow-a", false))
            .extracting(ft -> ft.getDestination().getId())
            .containsExactly("flow-b");

        flowTopologyRepository.save(flowA, List.of(createSimpleFlowTopology(tenant, "flow-a", "flow-c", namespace)));

        List<FlowTopology> list = flowTopologyRepository.findByFlow(tenant, namespace, "flow-a", false);
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getDestination().getId()).isEqualTo("flow-c");
    }

    @Test
    void shouldNotDeleteCounterpartEdgesWhenSavingAnotherFlow() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.tests";
        Flow flowA = Flow.builder().id("flow-a").namespace(namespace).tenantId(tenant).build();
        Flow flowC = Flow.builder().id("flow-c").namespace(namespace).tenantId(tenant).build();

        // flow-b is incident to both edges below, but only flow-a and flow-c ever get re-saved.
        flowTopologyRepository.save(flowA, List.of(createSimpleFlowTopology(tenant, "flow-a", "flow-b", namespace)));
        flowTopologyRepository.save(flowC, List.of(createSimpleFlowTopology(tenant, "flow-b", "flow-c", namespace)));

        // Re-saving flow-a's own edge set must not remove the flow-b -> flow-c edge it does not own.
        flowTopologyRepository.save(flowA, List.of(createSimpleFlowTopology(tenant, "flow-a", "flow-b", namespace)));

        assertThat(flowTopologyRepository.findByFlow(tenant, namespace, "flow-c", false))
            .extracting(ft -> ft.getSource().getId())
            .containsExactly("flow-b");
    }

    @Test
    void shouldScopeSaveDeleteToTenantNamespaceAndId() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String otherTenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.tests";
        Flow flowA = Flow.builder().id("flow-a").namespace(namespace).tenantId(tenant).build();
        Flow otherTenantFlowA = Flow.builder().id("flow-a").namespace(namespace).tenantId(otherTenant).build();

        flowTopologyRepository.save(flowA, List.of(createSimpleFlowTopology(tenant, "flow-a", "flow-b", namespace)));
        flowTopologyRepository.save(otherTenantFlowA, List.of(createSimpleFlowTopology(otherTenant, "flow-a", "flow-b", namespace)));

        // Saving (an empty topology for) flow-a in otherTenant must not touch flow-a's edges in tenant.
        flowTopologyRepository.save(otherTenantFlowA, List.of());

        assertThat(flowTopologyRepository.findByFlow(tenant, namespace, "flow-a", false)).hasSize(1);
        assertThat(flowTopologyRepository.findByFlow(otherTenant, namespace, "flow-a", false)).isEmpty();
    }

}
