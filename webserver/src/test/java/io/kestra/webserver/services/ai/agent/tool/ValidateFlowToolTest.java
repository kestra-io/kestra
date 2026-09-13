package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration check that {@code validate-flow} runs the real {@link io.kestra.core.services.FlowService}
 * validation: a well-formed flow reports valid, a malformed one reports errors.
 */
@KestraTest(environments = "memory")
class ValidateFlowToolTest {
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);
    private static final String VALID_YAML = """
        id: valid-flow
        namespace: io.kestra.test
        tasks:
          - id: hello
            type: io.kestra.plugin.core.log.Log
            message: hi
        """;

    @Inject
    private ValidateFlowTool tool;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReportValidWhenFlowIsWellFormed() {
        // When
        ValidateFlowTool.Result result = tool.validateFlow(VALID_YAML, CONTEXT);

        // Then
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldReportErrorsWhenFlowIsMalformed() {
        // When — missing the mandatory namespace and tasks
        ValidateFlowTool.Result result = tool.validateFlow("id: broken", CONTEXT);

        // Then
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }
}
