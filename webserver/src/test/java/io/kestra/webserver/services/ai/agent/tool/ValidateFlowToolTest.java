package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.models.flows.FlowSource;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.services.FlowService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidateFlowToolTest {
    private static final String TENANT = "main";
    private static final String YAML = "id: flow-1\nnamespace: io.kestra.test\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n    message: hi";

    private FlowService flowService;
    private ValidateFlowTool tool;

    @BeforeEach
    void setUp() {
        flowService = mock(FlowService.class);
        tool = new ValidateFlowTool(flowService);
        AgentCallContext.set(AgentCallContext.Context.ofTenant(TENANT));
    }

    @AfterEach
    void tearDown() {
        AgentCallContext.clear();
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        // When / Then
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReportValidWhenNoConstraints() {
        // Given — no constraint violations at all
        when(flowService.validate(eq(TENANT), any()))
            .thenReturn(List.of(ValidateConstraintViolation.builder().index(0).build()));

        // When
        String result = tool.validateFlow(YAML, null);

        // Then — the YAML was wrapped in a FlowSource and the flow reported valid
        assertThat(result).isEqualTo("The flow is valid.");
        ArgumentCaptor<List<FlowSource>> captor = ArgumentCaptor.forClass(List.class);
        verify(flowService).validate(eq(TENANT), captor.capture());
        assertThat(captor.getValue())
            .singleElement()
            .isEqualTo(new FlowSource(null, YAML));
    }

    @Test
    void shouldReportValidWithWarningsAndDeprecationsWhenNoErrors() {
        // Given — warnings, deprecations and infos but no constraint errors
        when(flowService.validate(eq(TENANT), any())).thenReturn(
            List.of(
                ValidateConstraintViolation.builder()
                    .index(0)
                    .warnings(List.of("task 'hello' is slow"))
                    .deprecationPaths(List.of("tasks[0].oldProp"))
                    .infos(List.of("io.kestra.core.tasks.log.Log is replaced by io.kestra.plugin.core.log.Log"))
                    .build()
            )
        );

        // When
        String result = tool.validateFlow(YAML, null);

        // Then
        assertThat(result).isEqualTo(
            "The flow is valid.\n"
                + "Warnings: task 'hello' is slow\n"
                + "Deprecated paths: tasks[0].oldProp\n"
                + "Infos: io.kestra.core.tasks.log.Log is replaced by io.kestra.plugin.core.log.Log"
        );
    }

    @Test
    void shouldReportErrorsWhenConstraintsExist() {
        // Given — a constraint violation
        when(flowService.validate(eq(TENANT), any())).thenReturn(
            List.of(
                ValidateConstraintViolation.builder()
                    .index(0)
                    .constraints("tasks: must not be empty")
                    .build()
            )
        );

        // When
        String result = tool.validateFlow("id: broken", null);

        // Then — errors reported, no "valid" line
        assertThat(result).isEqualTo("Errors: tasks: must not be empty");
        assertThat(result).doesNotContain("The flow is valid.");
    }
}
