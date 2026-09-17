package io.kestra.executor;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

@ExecutorStateMachineTest
class FailureStateMachineTest {
    private static Flow logFlow(String tenantId) {
        return Flow.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();
    }

    @Test
    void shouldFailExecutionWhenTaskFails(ExecutorStateMachineHarness harness) {
        Flow flow = logFlow(harness.tenantId());

        Execution running = harness.process(flow, Execution.newExecution(flow, Collections.emptyList()));
        assertThat(running.getState().getCurrent()).isEqualTo(State.Type.RUNNING);

        // The worker reports the task as FAILED; the executor fails the execution.
        TaskRun taskRun = running.getTaskRunList().getFirst();
        WorkerTaskResult failure = WorkerTaskResult.builder()
            .taskRun(taskRun.withState(State.Type.FAILED))
            .build();

        Execution failed = harness.process(flow, running, failure);
        assertThat(failed.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(failed.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
