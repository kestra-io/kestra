package io.kestra.plugin.core.flow;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.flows.FlowInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.History;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.InputAndOutput;
import io.kestra.core.runners.Services;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.services.VariablesService;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Slf4j
class SubflowTest {

    private static final State DEFAULT_SUCCESS_STATE = State.of(
        State.Type.SUCCESS,
        List.of(new State.History(State.Type.CREATED, Instant.now()), new State.History(State.Type.RUNNING, Instant.now()), new State.History(State.Type.SUCCESS, Instant.now()))
    );
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
        Mockito.when(runContext.logger()).thenReturn(log);
        Mockito.when(runContext.inputAndOutput()).thenReturn(inputAndOutput);

        Services services = Mockito.mock(Services.class);
        Mockito.when(services.variablesService()).thenReturn(new VariablesService());
        Mockito.when(runContext.services()).thenReturn(services);
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
            Execution.builder().build(),
            Collections.emptyMap()
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldOnlyReturnOutputsFromFlowOutputs() throws IllegalVariableEvaluationException {
        Output output = Output.builder().id("key").value("value").build();
        Mockito.when(runContext.render(Mockito.anyMap())).thenReturn(Map.of(output.getId(), output.getValue()));
        Mockito.when(inputAndOutput.typedOutputs(Mockito.any(FlowInterface.class), Mockito.any(), Mockito.anyMap())).thenReturn(Map.of("key", "value"));
        Flow flow = Flow.builder()
            .outputs(List.of(output))
            .build();

        // When
        Optional<SubflowExecutionResult> result = new Subflow().createSubflowExecutionResult(
            runContext,
            TaskRun.builder().state(DEFAULT_SUCCESS_STATE).namespace("io.kestra.test").flowId("flow").executionId("execution").taskId("task").id("id").build(),
            flow,
            Execution.builder().id(EXECUTION_ID).state(DEFAULT_SUCCESS_STATE).build(),
            Collections.emptyMap()
        );

        // Then
        assertTrue(result.isPresent());
        Map<String, Object> outputs = result.get().getOutputs();

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