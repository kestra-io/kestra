package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@KestraTest(environments = "memory")
class ListFlowDependenciesToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai.dependencies";

    @Inject
    private ListFlowDependenciesTool tool;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @Test
    @SuppressWarnings("unchecked")
    void shouldListUpstreamAndDownstreamFlowsWhenFlowHasDependencies() {
        FlowNode parent = createFlow();
        FlowNode child = createFlow();
        FlowNode grandChild = createFlow();
        saveDependency(parent, child, FlowRelation.FLOW_TASK);
        saveDependency(child, grandChild, FlowRelation.FLOW_TRIGGER);

        Map<String, Object> result = ToolCalls.call(tool, "list_flow_dependencies", """
            { "namespace": "%s", "flowId": "%s" }""".formatted(NAMESPACE, child.getId()), MAIN_TENANT);

        assertThat((List<Map<String, Object>>) result.get("dependencies"))
            .extracting(
                dependency -> ((Map<String, Object>) dependency.get("upstream")).get("id"),
                dependency -> ((Map<String, Object>) dependency.get("downstream")).get("id"),
                dependency -> dependency.get("relation")
            )
            .containsExactlyInAnyOrder(
                tuple(parent.getId(), child.getId(), "FLOW_TASK"),
                tuple(child.getId(), grandChild.getId(), "FLOW_TRIGGER")
            );
    }

    private FlowNode createFlow() {
        String flowId = "flow" + IdUtils.create().toLowerCase();
        return FlowNode.of(flowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(flowId, NAMESPACE))));
    }

    private void saveDependency(final FlowNode upstream, final FlowNode downstream, final FlowRelation relation) {
        flowTopologyRepository.save(
            FlowTopology.builder()
                .source(upstream)
                .destination(downstream)
                .relation(relation)
                .build()
        );
    }

    @Test
    void shouldRejectPageBelowOne() {
        assertThatThrownBy(() -> ToolCalls.call(tool, "list_flow_dependencies", """
            { "namespace": "any", "flowId": "any", "page": 0 }""", MAIN_TENANT))
            .hasMessageContaining("Page must be 1 or greater");
    }
}
