package io.kestra.executor.handler;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExecutionMessageHandlerTest {
    @Inject
    private ExecutionMessageHandler executionMessageHandler;

    @Test
    void shouldReturnNoExecutor() {
        var flow = Fixtures.flow();
        var execution = Execution.newExecution(flow, Collections.emptyList());

        var maybeExecutor = executionMessageHandler.handle(execution);

        assertThat(maybeExecutor.isEmpty()).isTrue();
    }
}