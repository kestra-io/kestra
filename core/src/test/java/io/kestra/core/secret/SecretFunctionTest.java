package io.kestra.core.secret;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
public class SecretFunctionTest {

    @Inject
    DispatchQueueInterface<LogEntry> logQueue;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private SecretService secretService;

    @Inject
    VariableRenderer variableRenderer;

    @Inject
    TaskOutputService taskOutputService;

    @Test
    @LoadFlows({ "flows/valids/secrets.yaml" })
    @EnabledIfEnvironmentVariable(named = "SECRET_MY_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "SECRET_NEW_LINE", matches = ".*")
    void getSecret() throws TimeoutException, QueueException, InternalException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "secrets");
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("value")).isEqualTo("secretValue");
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().get(2)).get("value"))
            .isEqualTo("passwordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlong");
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().get(3)).get("value")).isEqualTo("secretValue");
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().get(4))).isEmpty();
        assertThat(execution.getTaskRunList().get(4).getState().getCurrent()).isEqualTo(State.Type.WARNING);

        LogEntry matchingLog = TestsUtils.awaitLog(logs, logEntry -> logEntry.getTaskId() != null && logEntry.getTaskId().equals("log-secret"));
        assertThat(matchingLog.getMessage()).contains("***");
    }

    @Test
    void shouldGetSecretGivenExistingSubKey() throws IllegalVariableEvaluationException {
        // Given
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When / Then
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='string') }}", context)).isEqualTo("value");
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='number') }}", context)).isEqualTo("42");
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='array') }}", context)).isEqualTo("[\"one\",\"two\",\"three\"]");
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='object') }}", context)).isEqualTo("{\"f1\":\"value1\",\"f2\":\"value2\"}");
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='boolean') }}", context)).isEqualTo("true");
    }

    @Test
    void shouldFailedGivenNonExistingSubKey() {
        // Given
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When / Then
        Throwable cause = Assertions.assertThrows(IllegalVariableEvaluationException.class, () ->
        {
            variableRenderer.render("{{ secret('json-secret', subkey='missing') }}", context);
        }).getCause();
        assertThat(cause.getMessage()).isEqualTo("Cannot find secret sub-key 'missing' in secret 'json-secret'. ({{ secret('json-secret', subkey='missing') }}:1)");
    }

    @Test
    void shouldFailedGivenExistingButInvalidSubKey() {
        // Given
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When / Then
        Throwable cause = Assertions.assertThrows(IllegalVariableEvaluationException.class, () ->
        {
            variableRenderer.render("{{ secret('string-secret', subkey='???') }}", context);
        }).getCause();
        assertThat(cause.getMessage())
            .isEqualTo("Failed to read secret sub-key '???' from secret 'string-secret'. Ensure the secret contains valid JSON value. ({{ secret('string-secret', subkey='???') }}:1)");
    }

    @Test
    void getUnknownSecret() {
        var exception = assertThrows(SecretNotFoundException.class, () -> secretService.findSecret(null, null, "unknown_secret_key"));
        assertThat(exception.getMessage()).isEqualTo("Cannot find secret for key 'unknown_secret_key'.");
    }

    @MockBean(SecretService.class)
    public static class TestSecretService extends SecretService {

        private static final Map<String, String> SECRETS = Map.of(
            "io.kestra.unittest.json-secret", """
                {
                "string": "value",
                "number": 42,
                "boolean": true,
                "array": ["one", "two", "three"],
                "object": {"f1": "value1", "f2": "value2"}
                }
                """,
            "io.kestra.unittest.string-secret", "string-value"
        );

        public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
            Optional<String> optional = Optional.ofNullable(SECRETS.get(namespace + "." + key));
            if (optional.isPresent()) {
                return optional.get();
            }
            return super.findSecret(tenantId, namespace, key);
        }
    }
}
