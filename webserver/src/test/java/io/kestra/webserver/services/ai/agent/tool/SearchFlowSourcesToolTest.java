package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class SearchFlowSourcesToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai.search";

    @Inject
    private SearchFlowSourcesTool tool;

    @Inject
    private FlowService flowService;

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnOnlyFlowsWhoseSourceMatchesWhenSearchingText() throws Exception {
        String token = "needle" + IdUtils.create().toLowerCase();
        String matching = createFlow("hello " + token);
        String other = createFlow("hello world");

        Map<String, Object> result = ToolCalls.call(tool, "search_flow_sources", """
            { "query": "%s", "namespace": "%s" }""".formatted(token, NAMESPACE), MAIN_TENANT);

        List<Map<String, Object>> flows = (List<Map<String, Object>>) result.get("flows");
        assertThat(flows).extracting(flow -> flow.get("id")).containsExactly(matching).doesNotContain(other);
        assertThat((List<Map<String, Object>>) flows.getFirst().get("matches"))
            .singleElement()
            .satisfies(match -> assertThat((String) match.get("snippet")).contains("[mark]" + token + "[/mark]"));
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(1);
    }

    private String createFlow(final String message) throws Exception {
        String flowId = "flow" + IdUtils.create().toLowerCase();
        flowService.create(GenericFlow.fromYaml(MAIN_TENANT, """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: %s
            """.formatted(flowId, NAMESPACE, message)));
        return flowId;
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnNoFlowsWhenPageIsBeyondTheLastOne() throws Exception {
        String token = "needle" + IdUtils.create().toLowerCase();
        createFlow("hello " + token);

        Map<String, Object> result = ToolCalls.call(tool, "search_flow_sources", """
            { "query": "%s", "namespace": "%s", "page": 2 }""".formatted(token, NAMESPACE), MAIN_TENANT);

        assertThat((List<Map<String, Object>>) result.get("flows")).isEmpty();
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(1);
        assertThat(((Number) result.get("page")).intValue()).isEqualTo(2);
    }

    @Test
    void shouldRejectPageBelowOne() {
        assertThatThrownBy(() -> ToolCalls.call(tool, "search_flow_sources", """
            { "query": "any", "page": 0 }""", MAIN_TENANT))
            .hasMessageContaining("Page must be 1 or greater");
    }
}
