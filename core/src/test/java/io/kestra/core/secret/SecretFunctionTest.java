package io.kestra.core.secret;

import static org.hamcrest.MatcherAssert.assertThat;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
public class SecretFunctionTest {

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> logQueue;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private SecretService secretService;

    @Inject
    VariableRenderer variableRenderer;

    @Test
    @LoadFlows({"flows/valids/secrets.yaml"})
    @EnabledIfEnvironmentVariable(named = "SECRET_MY_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "SECRET_NEW_LINE", matches = ".*")
    void getSecret() throws TimeoutException, QueueException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "secrets");
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("value"), is("secretValue"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("value"), is("passwordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlong"));
        assertThat(execution.getTaskRunList().get(3).getOutputs().get("value"), is("secretValue"));
        assertThat(execution.getTaskRunList().get(4).getOutputs(), anEmptyMap());
        assertThat(execution.getTaskRunList().get(4).getState().getCurrent(), is(State.Type.WARNING));

        LogEntry matchingLog = TestsUtils.awaitLog(logs, logEntry -> logEntry.getTaskId() != null && logEntry.getTaskId().equals("log-secret"));
        receive.blockLast();
        assertThat(matchingLog.getMessage(), containsString("***"));
    }


    @Test
    void shouldGetSecretGivenExistingSubKey() throws IllegalVariableEvaluationException {
        // Given
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When / Then
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='string') }}", context), is("value"));
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='number') }}", context), is("42"));
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='array') }}", context), is("[\"one\",\"two\",\"three\"]"));
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='object') }}", context), is("{\"f1\":\"value1\",\"f2\":\"value2\"}"));
        assertThat(variableRenderer.render("{{ secret('json-secret', subkey='boolean') }}", context), is("true"));
    }

    @Test
    void shouldFailedGivenNonExistingSubKey() {
        // Given
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When / Then
        Throwable cause = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ secret('json-secret', subkey='missing') }}", context);
        }).getCause();
        assertThat(cause.getMessage(), is("Cannot find secret sub-key 'missing' in secret 'json-secret'. ({{ secret('json-secret', subkey='missing') }}:1)"));
    }

    @Test
    void shouldFailedGivenExistingButInvalidSubKey() {
        // Given
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When / Then
        Throwable cause = Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> {
            variableRenderer.render("{{ secret('string-secret', subkey='???') }}", context);
        }).getCause();
        assertThat(cause.getMessage(), is("Failed to read secret sub-key '???' from secret 'string-secret'. Ensure the secret contains valid JSON value. ({{ secret('string-secret', subkey='???') }}:1)"));
    }

    @Test
    void getUnknownSecret() {
        var exception = assertThrows(SecretNotFoundException.class, () -> secretService.findSecret(null, null, "unknown_secret_key"));
        assertThat(exception.getMessage(), is("Cannot find secret for key 'unknown_secret_key'."));
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
