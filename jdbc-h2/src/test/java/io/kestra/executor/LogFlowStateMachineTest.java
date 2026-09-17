package io.kestra.executor;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

@ExecutorStateMachineTest
class LogFlowStateMachineTest extends AbstractExecutorStateMachineTest {
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
    void shouldRunSingleTaskFlowToSuccess() {
        ExecutorStateMachineHarness harness = harnesses.create();

        Flow flow = logFlow(harness.tenantId());
        Execution created = Execution.newExecution(flow, Collections.emptyList());

        // Start it: the executor submits the task to a worker and parks at RUNNING.
        Execution running = harness.process(flow, created);
        assertThat(running.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(harness.emitted(WorkerJobEvent.class)).hasSize(1);

        // Complete the task the way a worker would, then let the executor finish the flow.
        TaskRun taskRun = running.getTaskRunList().getFirst();
        WorkerTaskResult result = WorkerTaskResult.builder()
            .taskRun(taskRun.withState(State.Type.SUCCESS))
            .build();

        Execution success = harness.process(flow, running, result);
        assertThat(success.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(success.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}
