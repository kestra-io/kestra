package io.kestra.executor;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

@ExecutorStateMachineTest
class ConcurrencyStateMachineTest extends AbstractExecutorStateMachineTest {
    private static Flow limitOneFlow(String tenantId) {
        return Flow.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .concurrency(Concurrency.builder().limit(1).behavior(Concurrency.Behavior.QUEUE).build())
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();
    }

    @Test
    void shouldQueueSecondExecutionThenReleaseItWhenFirstTerminates() {
        ExecutorStateMachineHarness harness = harnesses.create();
        Flow flow = limitOneFlow(harness.tenantId());

        // First execution claims the only slot and runs.
        Execution first = harness.process(flow, Execution.newExecution(flow, Collections.emptyList()));
        assertThat(first.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(harness.concurrencyRunning(flow)).isEqualTo(1);

        // Second execution finds the limit reached and is queued, without claiming a slot.
        Execution second = harness.process(flow, Execution.newExecution(flow, Collections.emptyList()));
        assertThat(second.getState().getCurrent()).isEqualTo(State.Type.QUEUED);
        assertThat(harness.concurrencyRunning(flow)).isEqualTo(1);

        // Completing the first execution releases the slot and pops the queued one back onto the
        // execution queue (a different execution: surfaced, not auto-run).
        TaskRun firstTask = first.getTaskRunList().getFirst();
        Execution firstDone = harness.process(
            flow,
            first,
            io.kestra.core.runners.WorkerTaskResult.builder().taskRun(firstTask.withState(State.Type.SUCCESS)).build()
        );
        assertThat(firstDone.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(harness.emitted(Execution.class))
            .extracting(Execution::getId)
            .contains(second.getId());
    }
}
