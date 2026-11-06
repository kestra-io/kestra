package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class ListFlowRevisionsToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai.revisions";

    @Inject
    private ListFlowRevisionsTool tool;

    @Inject
    private FlowService flowService;

    @Test
    @SuppressWarnings("unchecked")
    void shouldListRevisionsNewestFirstWhenFlowWasUpdated() throws Exception {
        String flowId = "flow" + IdUtils.create().toLowerCase();
        FlowWithSource previous = flowService.create(GenericFlow.fromYaml(MAIN_TENANT, source(flowId, "one")));
        previous = flowService.update(GenericFlow.fromYaml(MAIN_TENANT, source(flowId, "two")), previous);
        flowService.update(GenericFlow.fromYaml(MAIN_TENANT, source(flowId, "three")), previous);

        Map<String, Object> result = ToolCalls.call(tool, "list_flow_revisions", """
            { "namespace": "%s", "flowId": "%s" }""".formatted(NAMESPACE, flowId), MAIN_TENANT);

        assertThat((List<Map<String, Object>>) result.get("revisions"))
            .extracting(revision -> revision.get("revision"))
            .containsExactly(3, 2, 1);
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(3);
    }

    @Test
    void shouldFailWhenFlowDoesNotExist() {
        assertThatThrownBy(() -> ToolCalls.call(tool, "list_flow_revisions", """
            { "namespace": "%s", "flowId": "missing" }""".formatted(NAMESPACE), MAIN_TENANT))
            .hasMessageContaining("Flow not found");
    }

    private static String source(final String flowId, final String message) {
        return """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: %s
            """.formatted(flowId, NAMESPACE, message);
    }
}
