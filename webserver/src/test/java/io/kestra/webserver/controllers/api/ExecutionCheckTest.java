package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.plugin.core.debug.Return;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end coverage of the check behaviors applied when an execution is created. Runs against the real
 * executor ({@code startRunner = true}) so that the created execution reaches a terminal state and the
 * failure messages can be asserted.
 */
@KestraTest(startRunner = true)
class ExecutionCheckTest {

    private static final String NAMESPACE_ID = "io.othercompany";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    TestRunnerUtils runnerUtils;

    @Test
    void shouldNotBlockExecutionWhenCheckPasses() {
        String flowId = "flowWithPassingBlockCheck";

        createFlowWithCheck(flowId, Check.builder()
            .passIf("{{ [] | length == 0 }}")
            .message("never shown")
            .style(Check.Style.ERROR)
            .behavior(Check.Behavior.BLOCK_EXECUTION)
            .build());

        Execution execution = awaitTermination(startExecution(flowId));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldCreateFailedExecutionWhenFailCheckFails() {
        String flowId = "flowWithFailingFailCheck";

        createFlowWithCheck(flowId, Check.builder()
            .passIf("{{ [] | length > 0 }}")
            .message("No VM provided")
            .style(Check.Style.ERROR)
            .behavior(Check.Behavior.FAIL_EXECUTION)
            .build());

        Execution execution = awaitTermination(startExecution(flowId));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldNotFailExecutionWhenFailCheckPasses() {
        String flowId = "flowWithPassingFailCheck";

        createFlowWithCheck(flowId, Check.builder()
            .passIf("{{ [] | length == 0 }}")
            .message("never shown")
            .style(Check.Style.ERROR)
            .behavior(Check.Behavior.FAIL_EXECUTION)
            .build());

        Execution execution = awaitTermination(startExecution(flowId));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldCreateExecutionWhenCreateCheckFails() {
        String flowId = "flowWithFailingCreateCheck";

        createFlowWithCheck(flowId, Check.builder()
            .passIf("{{ [] | length > 0 }}")
            .message("No VM provided")
            .style(Check.Style.WARNING)
            .behavior(Check.Behavior.CREATE_EXECUTION)
            .build());

        Execution execution = awaitTermination(startExecution(flowId));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldCreateExecutionWhenCreateCheckPasses() {
        String flowId = "flowWithPassingCreateCheck";

        createFlowWithCheck(flowId, Check.builder()
            .passIf("{{ [] | length == 0 }}")
            .message("never shown")
            .style(Check.Style.WARNING)
            .behavior(Check.Behavior.CREATE_EXECUTION)
            .build());

        Execution execution = awaitTermination(startExecution(flowId));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldReturnFailedCheckMessageStyleAndBehaviorWhenValidatingInputs() {
        String flowId = "flowWithWarningCheck";

        createFlowWithCheck(flowId, Check.builder()
            .passIf("{{ [] | length > 0 }}")
            .message("No VM provided")
            .style(Check.Style.WARNING)
            .behavior(Check.Behavior.CREATE_EXECUTION)
            .build());

        ExecutionController.ApiValidateExecutionInputsResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/" + NAMESPACE_ID + "/" + flowId + "/validate", null)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            ExecutionController.ApiValidateExecutionInputsResponse.class
        );

        assertThat(response.checks())
            .singleElement()
            .satisfies(check ->
            {
                assertThat(check.message()).isEqualTo("No VM provided");
                assertThat(check.style()).isEqualTo(Check.Style.WARNING);
                assertThat(check.behavior()).isEqualTo(Check.Behavior.CREATE_EXECUTION);
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"when", "condition"})
    void shouldEvaluateDeprecatedAliasWhenBlockingExecution(String alias) {
        String flowId = "flowWithLegacyAlias" + Character.toUpperCase(alias.charAt(0)) + alias.substring(1) + "Check";
        String source = """
            id: %s
            namespace: %s
            tasks:
              - id: test
                type: io.kestra.plugin.core.debug.Return
                format: test
            checks:
              - %s: "{{ [] | length > 0 }}"
                message: Legacy alias blocked
                behavior: BLOCK_EXECUTION
                style: ERROR
            """.formatted(flowId, NAMESPACE_ID, alias);

        client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/flows", source).contentType(MediaType.APPLICATION_YAML_TYPE),
            Flow.class
        );

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> startExecution(flowId)
        );
        assertThat(e.getMessage()).contains("Legacy alias blocked");
    }

    private Execution startExecution(String flowId) {
        return client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/" + NAMESPACE_ID + "/" + flowId, null),
            Execution.class
        );
    }

    private Execution awaitTermination(Execution execution) {
        return runnerUtils.awaitExecution(
            e -> e.getState().isTerminated(),
            execution.withTenantId(MAIN_TENANT),
            Duration.ofSeconds(15)
        );
    }

    private void createFlowWithCheck(String flowId, Check check) {
        Flow flow = Flow.builder()
            .id(flowId)
            .tenantId(MAIN_TENANT)
            .namespace(NAMESPACE_ID)
            .checks(List.of(check))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();

        client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/flows", flow.sourceOrGenerateIfNull()).contentType(MediaType.APPLICATION_YAML_TYPE),
            Flow.class
        );
    }
}
