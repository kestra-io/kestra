package io.kestra.executor;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.flow.Parallel;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

@ExecutorStateMachineTest
class ParallelStateMachineTest extends AbstractExecutorStateMachineTest {
    private static Flow parallelFlow(String tenantId) {
        return Flow.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .tasks(List.of(Parallel.builder()
                .id("parallel")
                .type(Parallel.class.getName())
                .concurrent(Property.ofValue(0))
                .tasks(List.of(
                    Log.builder().id("a").type(Log.class.getName()).message("a").build(),
                    Log.builder().id("b").type(Log.class.getName()).message("b").build()
                ))
                .build()))
            .build();
    }

    private static WorkerTaskResult success(Execution execution, String taskId) {
        TaskRun taskRun = execution.getTaskRunList().stream()
            .filter(run -> run.getTaskId().equals(taskId))
            .findFirst()
            .orElseThrow();
        return WorkerTaskResult.builder().taskRun(taskRun.withState(State.Type.SUCCESS)).build();
    }

    @Test
    void shouldRunBothBranchesThenSucceed() {
        ExecutorStateMachineHarness harness = harnesses.create();
        Flow flow = parallelFlow(harness.tenantId());

        // Starting the flow submits both branches to the worker at once.
        Execution running = harness.process(flow, Execution.newExecution(flow, Collections.emptyList()));
        assertThat(running.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(harness.emitted(WorkerJobEvent.class)).hasSize(2);

        // Complete one branch: the execution stays RUNNING, waiting on the other.
        Execution afterA = harness.process(flow, running, success(running, "a"));
        assertThat(afterA.getState().getCurrent()).isEqualTo(State.Type.RUNNING);

        // Complete the second branch: the parallel resolves and the execution succeeds.
        Execution afterB = harness.process(flow, afterA, success(afterA, "b"));
        assertThat(afterB.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}
