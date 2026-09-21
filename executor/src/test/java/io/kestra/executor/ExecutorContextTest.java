package io.kestra.executor;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorContextTest {

    private static Execution blankExecution() {
        var task = Log.builder()
            .id("log")
            .type(Log.class.getName())
            .message("hello")
            .build();
        var flow = Flow.builder()
            .id("test-flow")
            .namespace("io.kestra.test")
            .tasks(List.of(task))
            .build();
        return Execution.newExecution(flow, Collections.emptyList());
    }

    @Test
    void shouldTrackInitialStateAsFirstTransition() {
        var execution = blankExecution(); // starts CREATED
        var ctx = new ExecutorContext(execution);

        assertThat(ctx.getStateTransitions()).containsExactly(State.Type.CREATED);
    }

    @Test
    void shouldNotKeepTheEntryExecutionWhenItWasNotTerminated() {
        var ctx = new ExecutorContext(blankExecution());

        // nothing to keep: an execution that was not terminated at entry cannot have been over
        // before this cycle, whatever its flow declares
        assertThat(ctx.getTerminalExecutionAtEntry()).isNull();
    }

    @Test
    void shouldKeepTheEntryExecutionWhenItWasAlreadyTerminated() {
        var terminated = blankExecution().withState(State.Type.SUCCESS);
        var ctx = new ExecutorContext(terminated);

        // its task runs are the only way to tell a duplicate cycle from the one completing the
        // afterExecution tasks, which run after the execution state is already terminal
        assertThat(ctx.getTerminalExecutionAtEntry()).isSameAs(terminated);
    }

    @Test
    void shouldAppendDistinctStateTransitions() {
        var execution = blankExecution();
        var ctx = new ExecutorContext(execution);

        ctx.withExecution(execution.withState(State.Type.RUNNING), "start");
        ctx.withExecution(execution.withState(State.Type.PAUSED), "pause");
        ctx.withExecution(execution.withState(State.Type.RUNNING), "resume");
        ctx.withExecution(execution.withState(State.Type.SUCCESS), "done");

        assertThat(ctx.getStateTransitions())
            .containsExactly(
                State.Type.CREATED,
                State.Type.RUNNING,
                State.Type.PAUSED,
                State.Type.RUNNING,
                State.Type.SUCCESS
            );
    }

    @Test
    void shouldNotDuplicateConsecutiveSameState() {
        var execution = blankExecution();
        var ctx = new ExecutorContext(execution);

        ctx.withExecution(execution.withState(State.Type.RUNNING), "first");
        ctx.withExecution(execution.withState(State.Type.RUNNING), "second"); // same state, ignored

        assertThat(ctx.getStateTransitions())
            .containsExactly(State.Type.CREATED, State.Type.RUNNING);
    }

    @Test
    void shouldKeepTheEntryStateAsFirstTransitionWhenExecutionAdvances() {
        var execution = blankExecution();
        var ctx = new ExecutorContext(execution);

        ctx.withExecution(execution.withState(State.Type.RUNNING), "running");
        ctx.withExecution(execution.withState(State.Type.SUCCESS), "success");

        assertThat(ctx.getStateTransitions()).containsExactly(State.Type.CREATED, State.Type.RUNNING, State.Type.SUCCESS);
    }
}
