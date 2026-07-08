package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest(startRunner = true)
class ExpressionControllerTest {
    private static final String TENANT_ID = "main";
    private static final String TESTS_FLOW_NS = "io.kestra.tests";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @MockBean(TenantService.class)
    public TenantService getTenantService() {
        return mock(TenantService.class);
    }

    @Inject
    private TenantService tenantService;

    @MockBean(TenantValidationFilter.class)
    public TenantValidationFilter getTenantValidationFilter() {
        return mock(TenantValidationFilter.class);
    }

    private static final String FLOW_SOURCE = """
        id: render-test
        namespace: io.kestra.tests
        variables:
          region: us-east-1
        tasks:
          - id: log
            type: io.kestra.plugin.core.log.Log
            message: "{{ vars.region }}"
        """;

    private ExpressionController.RenderedExpressions render(Map<String, Object> body) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/" + TENANT_ID + "/expressions/render", body)
                .contentType(MediaType.APPLICATION_JSON_TYPE),
            Argument.of(ExpressionController.RenderedExpressions.class)
        );
    }

    @Test
    void shouldRenderAgainstFlowSource() {
        when(tenantService.resolveTenant()).thenReturn(TENANT_ID);

        var result = render(
            Map.of(
                "flow", FLOW_SOURCE,
                "expressions", List.of(
                    "{{ vars.region }}",
                    "{{ flow.id }}",
                    "{{ secret('MY_KEY') }}",
                    "{{ now() }}",
                    "prefix-{{ vars.region }}-{{ now() }}"
                )
            )
        );

        Map<String, String> rendered = result.rendered();
        // flow-level variables and flow metadata resolve
        assertThat(rendered).containsEntry("{{ vars.region }}", "us-east-1");
        assertThat(rendered).containsEntry("{{ flow.id }}", "render-test");
        // secret is masked, never invoked
        assertThat(rendered).containsEntry("{{ secret('MY_KEY') }}", "[secret: MY_KEY]");
        // non-deterministic stays raw
        assertThat(rendered).containsEntry("{{ now() }}", "{{ now() }}");
        // mixed value is all-or-nothing: one unresolvable expression keeps the whole string raw
        assertThat(rendered).containsEntry("prefix-{{ vars.region }}-{{ now() }}", "prefix-{{ vars.region }}-{{ now() }}");
    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml" })
    void shouldRenderAgainstExecution() throws TimeoutException, QueueException {
        when(tenantService.resolveTenant()).thenReturn(TENANT_ID);
        Execution execution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        var result = render(
            Map.of(
                "executionId", execution.getId(),
                "expressions", List.of("{{ execution.id }}", "{{ flow.id }}")
            )
        );

        Map<String, String> rendered = result.rendered();
        assertThat(rendered).containsEntry("{{ execution.id }}", execution.getId());
        assertThat(rendered).containsEntry("{{ flow.id }}", "minimal");
    }

    @Test
    @LoadFlows({ "flows/valids/variables.yaml" })
    void shouldRenderAgainstFlowByNamespaceAndId() {
        when(tenantService.resolveTenant()).thenReturn(TENANT_ID);

        var result = render(
            Map.of(
                "namespace", TESTS_FLOW_NS,
                "flowId", "variables",
                "expressions", List.of("{{ vars.first }}", "{{ flow.id }}", "{{ now() }}")
            )
        );

        Map<String, String> rendered = result.rendered();
        assertThat(rendered).containsEntry("{{ vars.first }}", "1");
        assertThat(rendered).containsEntry("{{ flow.id }}", "variables");
        assertThat(rendered).containsEntry("{{ now() }}", "{{ now() }}");
    }

    @Test
    void shouldReturnEmptyContextWhenNeitherFlowNorExecutionProvided() {
        when(tenantService.resolveTenant()).thenReturn(TENANT_ID);

        var result = render(
            Map.of(
                "expressions", List.of("literal", "{{ vars.region }}", "{{ now() }}")
            )
        );

        Map<String, String> rendered = result.rendered();
        // no context → literals pass through, everything resolvable-only stays raw
        assertThat(rendered).containsEntry("literal", "literal");
        assertThat(rendered).containsEntry("{{ vars.region }}", "{{ vars.region }}");
        assertThat(rendered).containsEntry("{{ now() }}", "{{ now() }}");
    }
}
