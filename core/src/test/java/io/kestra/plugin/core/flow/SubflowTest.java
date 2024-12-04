package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.Type;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.SubflowExecutionResult;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class SubflowTest {
    private static final State DEFAULT_SUCCESS_STATE = State.of(State.Type.SUCCESS, List.of(new State.History(State.Type.CREATED, Instant.now()), new State.History(State.Type.RUNNING, Instant.now()), new State.History(State.Type.SUCCESS, Instant.now())));
    public static final String EXECUTION_ID = "executionId";

    @Inject
    private RunContextFactory runContextFactory;


    @Test
    void shouldNotReturnResultForExecutionNotTerminated() {
        TaskRun taskRun = TaskRun
            .builder()
            .state(State.of(State.Type.CREATED, Collections.emptyList()))
            .build();
        RunContext runContext = runContextFactory.of();

        Optional<SubflowExecutionResult> result = new Subflow().createSubflowExecutionResult(
            runContext,
            taskRun,
            Flow.builder().build(),
            Execution.builder().build()
        );

        assertThat(result, is(Optional.empty()));
    }

    @SuppressWarnings("deprecation")
    @Test
    void shouldNotReturnOutputsForSubflowOutputsDisabled() {
        Map<String, Object> outputs = Map.of("key", "value");
        Subflow subflow = Subflow.builder()
            .outputs(outputs)
            .build();
        DefaultRunContext defaultRunContext = (DefaultRunContext) runContextFactory.of();
        DefaultRunContext runContext = Mockito.mock(DefaultRunContext.class);
        Mockito.when(runContext.pluginConfiguration(Subflow.PLUGIN_FLOW_OUTPUTS_ENABLED)).thenReturn(Optional.of(false));
        Mockito.when(runContext.getApplicationContext()).thenReturn(defaultRunContext.getApplicationContext());

        // When
        Optional<SubflowExecutionResult> result = subflow.createSubflowExecutionResult(
            runContext,
            TaskRun.builder().state(DEFAULT_SUCCESS_STATE).build(),
            Flow.builder().build(),
            Execution.builder().id(EXECUTION_ID).state(DEFAULT_SUCCESS_STATE).build()
        );

        // Then
        assertTrue(result.isPresent());
        Map<String, Object> expected = Subflow.Output.builder()
            .executionId(EXECUTION_ID)
            .state(DEFAULT_SUCCESS_STATE.getCurrent())
            .build()
            .toMap();
        assertThat(result.get().getParentTaskRun().getOutputs(), is(expected));

        assertThat(result.get().getParentTaskRun().getAttempts().get(0).getState().getHistories(), Matchers.contains(
            hasProperty("state", is(State.Type.CREATED)),
            hasProperty("state", is(State.Type.RUNNING)),
            hasProperty("state", is(State.Type.SUCCESS))
        ));
    }

    @SuppressWarnings("deprecation")
    @Test
    void shouldReturnOutputsForSubflowOutputsEnabled() {
        // Given
        Map<String, Object> outputs = Map.of("key", "value");
        RunContext runContext = runContextFactory.of(outputs);

        Subflow subflow = Subflow.builder()
            .outputs(outputs)
            .build();

        // When
        Optional<SubflowExecutionResult> result = subflow.createSubflowExecutionResult(
            runContext,
            TaskRun.builder().state(DEFAULT_SUCCESS_STATE).build(),
            Flow.builder().build(),
            Execution.builder().id(EXECUTION_ID).state(DEFAULT_SUCCESS_STATE).build()
        );

        // Then
        assertTrue(result.isPresent());
        Map<String, Object> expected = Subflow.Output.builder()
            .executionId(EXECUTION_ID)
            .state(DEFAULT_SUCCESS_STATE.getCurrent())
            .outputs(outputs)
            .build()
            .toMap();
        assertThat(result.get().getParentTaskRun().getOutputs(), is(expected));

        assertThat(result.get().getParentTaskRun().getAttempts().get(0).getState().getHistories(), Matchers.contains(
            hasProperty("state", is(State.Type.CREATED)),
            hasProperty("state", is(State.Type.RUNNING)),
            hasProperty("state", is(State.Type.SUCCESS))
        ));
    }

    @Test
    void shouldOnlyReturnOutputsFromFlowOutputs() {
        // Given
        Output output = Output.builder().id("key").value("value").type(Type.STRING).build();
        RunContext runContext = runContextFactory.of(Map.of(output.getId(), output.getValue()));
        Flow flow = Flow.builder()
            .outputs(List.of(output))
            .build();

        // When
        Optional<SubflowExecutionResult> result = new Subflow().createSubflowExecutionResult(
            runContext,
            TaskRun.builder().state(DEFAULT_SUCCESS_STATE).build(),
            flow,
            Execution.builder().id(EXECUTION_ID).state(DEFAULT_SUCCESS_STATE).build()
        );

        // Then
        assertTrue(result.isPresent());
        Map<String, Object> outputs = result.get().getParentTaskRun().getOutputs();

        Map<String, Object> expected = Subflow.Output.builder()
            .executionId(EXECUTION_ID)
            .state(DEFAULT_SUCCESS_STATE.getCurrent())
            .outputs(Map.of(output.getId(), output.getValue()))
            .build()
            .toMap();
        assertThat(outputs, is(expected));

        assertThat(result.get().getParentTaskRun().getAttempts().get(0).getState().getHistories(), Matchers.contains(
            hasProperty("state", is(State.Type.CREATED)),
            hasProperty("state", is(State.Type.RUNNING)),
            hasProperty("state", is(State.Type.SUCCESS))
        ));
    }
}