package io.kestra.plugin.core.flow;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.History;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.InputAndOutput;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.services.VariablesService;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubflowTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubflowTest.class);

    private static final State DEFAULT_SUCCESS_STATE = State.of(State.Type.SUCCESS, List.of(new State.History(State.Type.CREATED, Instant.now()), new State.History(State.Type.RUNNING, Instant.now()), new State.History(State.Type.SUCCESS, Instant.now())));
    public static final String EXECUTION_ID = "executionId";

    @Mock
    private DefaultRunContext runContext;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private InputAndOutput inputAndOutput;

    @BeforeEach
    void beforeEach() {
        Mockito.when(applicationContext.getBean(VariablesService.class)).thenReturn(new VariablesService());
        Mockito.when(runContext.logger()).thenReturn(LOG);
        Mockito.when(runContext.getApplicationContext()).thenReturn(applicationContext);
        Mockito.when(runContext.inputAndOutput()).thenReturn(inputAndOutput);
    }

    @Test
    void shouldNotReturnResultForExecutionNotTerminated() {
        TaskRun taskRun = TaskRun
            .builder()
            .state(State.of(State.Type.CREATED, Collections.emptyList()))
            .build();

        Optional<SubflowExecutionResult> result = new Subflow().createSubflowExecutionResult(
            runContext,
            taskRun,
            Flow.builder().build(),
            Execution.builder().build()
        );

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("deprecation")
    @Test
    void shouldNotReturnOutputsForSubflowOutputsDisabled() {
        // Given
        Mockito.when(applicationContext.getProperty(Subflow.PLUGIN_FLOW_OUTPUTS_ENABLED, Boolean.class))
            .thenReturn(Optional.of(false));

        Map<String, Object> outputs = Map.of("key", "value");
        Subflow subflow = Subflow.builder()
            .outputs(outputs)
            .build();

        // When
        Optional<SubflowExecutionResult> result = subflow.createSubflowExecutionResult(
            runContext,
            TaskRun.builder().state(DEFAULT_SUCCESS_STATE).namespace("io.kestra.test").flowId("flow").executionId("execution").taskId("task").id("id").build(),
            Flow.builder().build(),
            Execution.builder().id(EXECUTION_ID).state(DEFAULT_SUCCESS_STATE).build()
        );

        // Then
        assertTrue(result.isPresent());
        Map<String, Object> expected = Subflow.Output.builder()
            .executionId(EXECUTION_ID)
            .state(DEFAULT_SUCCESS_STATE.getCurrent())
            .outputs(Collections.emptyMap())
            .build()
            .toMap();
        assertThat(result.get().getParentTaskRun().getOutputs()).containsAllEntriesOf(expected);

        assertThat(result.get().getParentTaskRun().getAttempts().getFirst().getState().getHistories())
            .extracting(History::getState)
            .containsExactly(
                State.Type.CREATED,
                State.Type.RUNNING,
                State.Type.SUCCESS
            );
    }

    @SuppressWarnings("deprecation")
    @Test
    void shouldReturnOutputsForSubflowOutputsEnabled() throws IllegalVariableEvaluationException {
        // Given
        Mockito.when(applicationContext.getProperty(Subflow.PLUGIN_FLOW_OUTPUTS_ENABLED, Boolean.class))
            .thenReturn(Optional.of(true));

        Map<String, Object> outputs = Map.of("key", "value");
        Mockito.when(runContext.render(Mockito.anyMap())).thenReturn(outputs);
        Mockito.when(inputAndOutput.renderOutputs(Mockito.anyList())).thenReturn(Map.of("key", "value"));

        Subflow subflow = Subflow.builder()
            .outputs(outputs)
            .build();

        // When
        Optional<SubflowExecutionResult> result = subflow.createSubflowExecutionResult(
            runContext,
            TaskRun.builder().state(DEFAULT_SUCCESS_STATE).namespace("io.kestra.test").flowId("flow").executionId("execution").taskId("task").id("id").build(),
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
        assertThat(result.get().getParentTaskRun().getOutputs()).containsAllEntriesOf(expected);

        assertThat(result.get().getParentTaskRun().getAttempts().get(0).getState().getHistories())
            .extracting(History::getState)
            .containsExactly(
                State.Type.CREATED,
                State.Type.RUNNING,
                State.Type.SUCCESS
            );
    }

    @Test
    void shouldOnlyReturnOutputsFromFlowOutputs() throws IllegalVariableEvaluationException {
        // Given
        Mockito.when(applicationContext.getProperty(Subflow.PLUGIN_FLOW_OUTPUTS_ENABLED, Boolean.class))
            .thenReturn(Optional.of(true));

        Output output = Output.builder().id("key").value("value").build();
        Mockito.when(runContext.render(Mockito.anyMap())).thenReturn(Map.of(output.getId(), output.getValue()));
        Mockito.when(inputAndOutput.typedOutputs(Mockito.any(), Mockito.any(), Mockito.anyMap())).thenReturn(Map.of("key", "value"));
        Flow flow = Flow.builder()
            .outputs(List.of(output))
            .build();

        // When
        Optional<SubflowExecutionResult> result = new Subflow().createSubflowExecutionResult(
            runContext,
            TaskRun.builder().state(DEFAULT_SUCCESS_STATE).namespace("io.kestra.test").flowId("flow").executionId("execution").taskId("task").id("id").build(),
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
        assertThat(outputs).containsAllEntriesOf(expected);

        assertThat(result.get().getParentTaskRun().getAttempts().getFirst().getState().getHistories())
            .extracting(History::getState)
            .containsExactly(
                State.Type.CREATED,
                State.Type.RUNNING,
                State.Type.SUCCESS
            );
    }
}