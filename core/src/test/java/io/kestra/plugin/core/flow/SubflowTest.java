package io.kestra.plugin.core.flow;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.SubflowExecutionResult;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubflowTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubflowTest.class);

    private static final State DEFAULT_SUCCESS_STATE = State.of(State.Type.SUCCESS, Collections.emptyList());
    public static final String EXECUTION_ID = "executionId";

    @Mock
    private DefaultRunContext runContext;

    @Mock
    private ApplicationContext applicationContext;

    @BeforeEach
    void beforeEach() {
        Mockito.when(runContext.logger()).thenReturn(LOG);
        Mockito.when(runContext.getApplicationContext()).thenReturn(applicationContext);
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

        assertThat(result, is(Optional.empty()));
    }

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
            TaskRun.builder().state(DEFAULT_SUCCESS_STATE).build(),
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
        assertThat(result.get().getParentTaskRun().getOutputs(), is(expected));
    }

    @Test
    void shouldReturnOutputsForSubflowOutputsEnabled() throws IllegalVariableEvaluationException {
        // Given
        Mockito.when(applicationContext.getProperty(Subflow.PLUGIN_FLOW_OUTPUTS_ENABLED, Boolean.class))
            .thenReturn(Optional.of(true));

        Map<String, Object> outputs = Map.of("key", "value");
        Mockito.when(runContext.render(Mockito.anyMap())).thenReturn(outputs);


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
    }

    @Test
    void shouldOnlyReturnOutputsFromFlowOutputs() throws IllegalVariableEvaluationException {
        // Given
        Mockito.when(applicationContext.getProperty(Subflow.PLUGIN_FLOW_OUTPUTS_ENABLED, Boolean.class))
            .thenReturn(Optional.of(true));

        Output output = Output.builder().id("key").value("value").build();
        Mockito.when(runContext.render(Mockito.anyMap())).thenReturn(Map.of(output.getId(), output.getValue()));
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
    }
}