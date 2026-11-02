package io.kestra.executor.statemachine;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.plugin.core.flow.Loop;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class LoopValuesTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String VALUES_URI = "kestra:///io/kestra/tests/values.ion";

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void anEmptyUriLoopSucceedsWithoutSpawningAnIteration() throws Exception {
        // Given: the values file exists but holds no record
        storageReturns("");
        FlowWithSource flow = Flows.of(loop(VALUES_URI));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When
        harness.run(created, ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertThat(loopTaskRun(created).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(harness.workerJobEventQueue().emittedMessages()).as("no iteration means no task ever reaches a worker").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "[]", "{}" })
    void anEmptyInlineLoopSucceedsWithoutSpawningAnIteration(String values) {
        // Given
        FlowWithSource flow = Flows.of(loop(values));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When
        harness.run(created, ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertThat(loopTaskRun(created).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(harness.workerJobEventQueue().emittedMessages()).isEmpty();
    }

    @Test
    void aUriLoopRunsOneIterationPerRecord() throws Exception {
        // Given
        storageReturns("\"a\"\n\"b\"\n");
        FlowWithSource flow = Flows.of(loop(VALUES_URI));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When
        harness.run(created, ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertThat(harness.workerJobEventQueue().emittedMessages()).as("one worker task per record").hasSize(2);
        assertThat(loopTaskRun(created).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    private void storageReturns(String ion) throws Exception {
        Mockito.when(harness.storage().getFile(URI.create(VALUES_URI)))
            .thenAnswer(invocation -> new ByteArrayInputStream(ion.getBytes(StandardCharsets.UTF_8)));
    }

    private TaskRun loopTaskRun(Execution execution) {
        return harness.executionStateStore().findByIdWithoutAcl(execution.getId()).getTaskRunList().stream()
            .filter(taskRun -> taskRun.getTaskId().equals("loop"))
            .findFirst()
            .orElseThrow();
    }

    private static Loop loop(Object values) {
        return Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(values)
            .tasks(List.of(Flows.log("item")))
            .build();
    }
}
