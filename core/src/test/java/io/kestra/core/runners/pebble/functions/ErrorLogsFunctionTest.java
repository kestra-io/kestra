package io.kestra.core.runners.pebble.functions;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.runners.VariableRenderer;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Property(name = "kestra.server-type", value = "WORKER")
@Execution(ExecutionMode.SAME_THREAD)
class ErrorLogsFunctionTest {
    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    private VariableRenderer variableRenderer;

    @AfterEach
    void tearDown() {
        logRepository.deleteByQuery("dev", "execution", null, null, (Level) null, null);
    }

    @Test
    void shouldReturnNothingWhenNoErrors() throws IllegalVariableEvaluationException {
        logRepository.save(logEntry(Level.INFO, "some log message"));
        Map<String, Object> variables = Map.of(
            "flow", Map.of("tenantId", "dev"),
            "execution", Map.of("id", "execution")
        );

        String render = variableRenderer.render("{{ errorLogs() }}", variables);

        assertThat(render).isEqualTo("[]");
    }

    @Test
    void shouldReturnErrorsWhenExistsErrors() throws IllegalVariableEvaluationException {
        logRepository.save(logEntry(Level.INFO, "some log message"));
        logRepository.save(logEntry(Level.ERROR, "first error message"));
        logRepository.save(logEntry(Level.ERROR, "second error message"));
        Map<String, Object> variables = Map.of(
            "flow", Map.of("tenantId", "dev"),
            "execution", Map.of("id", "execution")
        );

        String render = variableRenderer.render("{{ errorLogs() }}", variables);

        assertThat(render).contains("first error message");
        assertThat(render).contains("second error message");
    }

    private LogEntry logEntry(Level level, String message) {
        return LogEntry.builder().tenantId("dev").namespace("namespace").flowId("flow").executionId("execution").timestamp(Instant.now()).level(level).message(message).build();
    }
}