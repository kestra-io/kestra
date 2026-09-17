package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
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
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

@ExecutorStateMachineTest
class PauseStateMachineTest {
    private static Flow pauseFlow(String tenantId) {
        return Flow.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .tasks(List.of(
                Pause.builder()
                    .id("pause")
                    .type(Pause.class.getName())
                    .pauseDuration(Property.ofValue(Duration.ofMinutes(5)))
                    .build(),
                Log.builder().id("after").type(Log.class.getName()).message("resumed").build()
            ))
            .build();
    }

    @Test
    void shouldResumePausedExecutionWhenDelayFires(ExecutorStateMachineHarness harness) {
        Flow flow = pauseFlow(harness.tenantId());

        // The Pause task moves the execution to PAUSED and stores a resume delay.
        Execution paused = harness.process(flow, Execution.newExecution(flow, Collections.emptyList()));
        assertThat(paused.getState().getCurrent()).isEqualTo(State.Type.PAUSED);

        // Advance the clock past the pause duration: the resume delay fires and the flow continues.
        Execution resumed = harness.fireExpiredDelays(Instant.now().plus(Duration.ofHours(1)), paused);
        assertThat(resumed.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(harness.emitted(WorkerJobEvent.class)).isNotEmpty();

        // Complete the after-pause task.
        TaskRun after = resumed.getTaskRunList().stream()
            .filter(run -> run.getTaskId().equals("after"))
            .findFirst()
            .orElseThrow();
        Execution success = harness.process(
            flow,
            resumed,
            WorkerTaskResult.builder().taskRun(after.withState(State.Type.SUCCESS)).build()
        );
        assertThat(success.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}
