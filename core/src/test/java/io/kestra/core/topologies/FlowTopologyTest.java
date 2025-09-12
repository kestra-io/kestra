package io.kestra.core.topologies;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class FlowTopologyTest {
    @Inject
    private FlowService flowService;
    @Inject
    private FlowTopologyService flowTopologyService;
    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @Test
    void should_findDependencies_simpleCase() throws FlowProcessingException {
        // Given
        var tenantId = randomTenantId();
        var child = flowService.importFlow(tenantId,
            """
                id: child
                namespace: io.kestra.unittest
                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                """);
        var parent = flowService.importFlow(tenantId, """
            id: parent
            namespace: io.kestra.unittest
            tasks:
              - id: subflow
                type: io.kestra.core.tasks.flows.Flow
                flowId: child
                namespace: io.kestra.unittest
            """);
        var unrelatedFlow = flowService.importFlow(tenantId, """
            id: unrelated_flow
            namespace: io.kestra.unittest
            tasks:
              - id: download
                type: io.kestra.plugin.core.http.Download
            """);

        // When
        computeAndSaveTopologies(List.of(child, parent, unrelatedFlow));
        System.out.println();
        flowTopologyRepository.findAll(tenantId).forEach(topology -> {
            System.out.println(FlowTopologyTestData.of(topology));
        });

        var dependencies = flowService.findDependencies(tenantId, "io.kestra.unittest", parent.getId(), false, true);
        flowTopologyRepository.findAll(tenantId).forEach(topology -> {
            System.out.println(FlowTopologyTestData.of(topology));
        });

        // Then
        assertThat(dependencies.map(FlowTopologyTestData::of))
            .containsExactlyInAnyOrder(
                new FlowTopologyTestData(parent, child)
            );
    }

    @Test
    void should_findDependencies_subchildAndSuperParent() throws FlowProcessingException {
        // Given
        var tenantId = randomTenantId();
        var subChild = flowService.importFlow(tenantId,
            """
                id: sub_child
                namespace: io.kestra.unittest
                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                """);
        var child = flowService.importFlow(tenantId,
            """
                id: child
                namespace: io.kestra.unittest
                tasks:
                  - id: subflow
                    type: io.kestra.core.tasks.flows.Flow
                    flowId: sub_child
                    namespace: io.kestra.unittest
                """);
        var superParent = flowService.importFlow(tenantId, """
            id: super_parent
            namespace: io.kestra.unittest
            tasks:
              - id: subflow
                type: io.kestra.core.tasks.flows.Flow
                flowId: parent
                namespace: io.kestra.unittest
            """);
        var parent = flowService.importFlow(tenantId, """
            id: parent
            namespace: io.kestra.unittest
            tasks:
              - id: subflow
                type: io.kestra.core.tasks.flows.Flow
                flowId: child
                namespace: io.kestra.unittest
            """);
        var unrelatedFlow = flowService.importFlow(tenantId, """
            id: unrelated_flow
            namespace: io.kestra.unittest
            tasks:
              - id: download
                type: io.kestra.plugin.core.http.Download
            """);

        // When
        computeAndSaveTopologies(List.of(subChild, child, superParent, parent, unrelatedFlow));
        System.out.println();
        flowTopologyRepository.findAll(tenantId).forEach(topology -> {
            System.out.println(FlowTopologyTestData.of(topology));
        });
        System.out.println();

        var dependencies = flowService.findDependencies(tenantId, "io.kestra.unittest", parent.getId(), false, true);
        flowTopologyRepository.findAll(tenantId).forEach(topology -> {
            System.out.println(FlowTopologyTestData.of(topology));
        });

        // Then
        assertThat(dependencies.map(FlowTopologyTestData::of))
            .containsExactlyInAnyOrder(
                new FlowTopologyTestData(superParent, parent),
                new FlowTopologyTestData(parent, child),
                new FlowTopologyTestData(child, subChild)
            );
    }

    @Test
    void should_findDependencies_cyclicTriggers() throws FlowProcessingException {
        // Given
        var tenantId = randomTenantId();
        var triggeredFlowOne = flowService.importFlow(tenantId,
            """
                id: triggered_flow_one
                namespace: io.kestra.unittest
                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                triggers:
                  - id: listen
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                """);
        var triggeredFlowTwo = flowService.importFlow(tenantId, """
            id: triggered_flow_two
            namespace: io.kestra.unittest
            tasks:
              - id: download
                type: io.kestra.plugin.core.http.Download
            triggers:
              - id: listen
                type: io.kestra.plugin.core.trigger.Flow
                conditions:
                  - type: io.kestra.plugin.core.condition.ExecutionStatus
                    in:
                      - FAILED
            """);

        // When
        computeAndSaveTopologies(List.of(triggeredFlowOne, triggeredFlowTwo));

        flowTopologyRepository.findAll(tenantId).forEach(topology -> {
            System.out.println(FlowTopologyTestData.of(topology));
        });

        var dependencies = flowService.findDependencies(tenantId, "io.kestra.unittest", triggeredFlowTwo.getId(), false, true).toList();

        flowTopologyRepository.findAll(tenantId).forEach(topology -> {
            System.out.println(FlowTopologyTestData.of(topology));
        });

        // Then
        assertThat(dependencies.stream().map(FlowTopologyTestData::of))
            .containsExactlyInAnyOrder(
                new FlowTopologyTestData(triggeredFlowTwo, triggeredFlowOne),
                new FlowTopologyTestData(triggeredFlowOne, triggeredFlowTwo)
            );

    }

    /**
     * this function mimics the production behaviour
     */
    private void computeAndSaveTopologies(List<@NotNull FlowWithSource> flows) {
        flows.forEach(flow ->
            flowTopologyService
                .topology(
                    flow,
                    flows
                ).distinct()
                .forEach(topology -> flowTopologyRepository.save(topology))
            );
    }

    private static String randomTenantId() {
        return FlowTopologyTest.class + IdUtils.create();
    }


    record FlowTopologyTestData(String sourceUid, String destinationUid) {
        public FlowTopologyTestData(FlowWithSource parent, FlowWithSource child) {
            this(parent.uidWithoutRevision(), child.uidWithoutRevision());
        }

        public static FlowTopologyTestData of(FlowTopology flowTopology) {
            return new FlowTopologyTestData(flowTopology.getSource().getUid(), flowTopology.getDestination().getUid());
        }

        @Override
        public String toString() {
            return sourceUid + " -> " + destinationUid;
        }
    }
}
