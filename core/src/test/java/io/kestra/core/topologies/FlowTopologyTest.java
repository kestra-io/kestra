package io.kestra.core.topologies;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
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

        var dependencies = flowService.findDependencies(tenantId, "io.kestra.unittest", parent.getId(), false, true);

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

        var dependencies = flowService.findDependencies(tenantId, "io.kestra.unittest", parent.getId(), false, true);

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

        var dependencies = flowService.findDependencies(tenantId, "io.kestra.unittest", triggeredFlowTwo.getId(), false, true).toList();

        // Then
        assertThat(dependencies.stream().map(FlowTopologyTestData::of))
            .containsExactlyInAnyOrder(
                new FlowTopologyTestData(triggeredFlowTwo, triggeredFlowOne),
                new FlowTopologyTestData(triggeredFlowOne, triggeredFlowTwo)
            );

    }

    @Test
    void flowTriggerWithTargetFlow() throws FlowProcessingException {
        // Given
        var tenantId = randomTenantId();
        var parent = flowService.importFlow(tenantId,
            """
                id: parent
                namespace: io.kestra.unittest
                inputs:
                  - id: a
                    type: BOOL
                    defaults: true

                  - id: b
                    type: BOOL
                    defaults: "{{ inputs.a == true }}"
                    dependsOn:
                      inputs:
                        - a
                tasks:
                  - id: helloA
                    type: io.kestra.plugin.core.log.Log
                    message: Hello A
                """);
        var child = flowService.importFlow(tenantId, """
            id: child
            namespace: io.kestra.unittest
            tasks:
              - id: helloB
                type: io.kestra.plugin.core.log.Log
                message: Hello B
            triggers:
              - id: release
                type: io.kestra.plugin.core.trigger.Flow
                states:
                  - SUCCESS
                preconditions:
                  id: flows
                  flows:
                   - namespace: io.kestra.unittest
                     flowId: parent
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

        var dependencies = flowService.findDependencies(tenantId, "io.kestra.unittest", parent.getId(), false, true);

        // Then
        assertThat(dependencies.map(FlowTopologyTestData::of))
            .containsExactlyInAnyOrder(
                new FlowTopologyTestData(parent, child)
            );
    }

    @Test
    void testNamespaceGraph() throws FlowProcessingException {
        var tenantId = randomTenantId();

        var subChild = flowService.importFlow(tenantId,
            """
                id: sub_child
                namespace: io.kestra.unittest.sub
                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: Sub Child
                """);

        var child = flowService.importFlow(tenantId,
            """
                id: child
                namespace: io.kestra.unittest
                tasks:
                  - id: callSub
                    type: io.kestra.core.tasks.flows.Flow
                    flowId: sub_child
                    namespace: io.kestra.unittest.sub
                """);

        var parent = flowService.importFlow(tenantId,
            """
                id: parent
                namespace: io.kestra.unittest
                tasks:
                  - id: callChild
                    type: io.kestra.core.tasks.flows.Flow
                    flowId: child
                    namespace: io.kestra.unittest
                """);

        var unrelated = flowService.importFlow(tenantId,
            """
                id: unrelated
                namespace: io.kestra.unittest
                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: Not part of deps
                """);

        computeAndSaveTopologies(List.of(subChild, child, parent, unrelated));

        FlowTopologyGraph graph = flowTopologyService.namespaceGraph(tenantId, "io.kestra.unittest");

        assertThat(graph.getNodes())
            .extracting(FlowNode::getId)
            .contains("parent", "child", "sub_child", "unrelated");

        assertThat(graph.getEdges().size()).isEqualTo(2);
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
