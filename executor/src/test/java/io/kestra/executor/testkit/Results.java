package io.kestra.executor.testkit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutorContext;

/**
 * Builds the {@link WorkerTaskResult}s a worker would emit, from the worker tasks the executor
 * emitted. Attempt end dates are explicit so retry-date assertions are deterministic arithmetic.
 */
public final class Results {
    private Results() {
        // utility class pattern
    }

    public static WorkerTaskResult failed(ExecutorContext.ExecutorWorkerTask emitted, Instant attemptEnd) {
        return terminated(emitted, State.Type.FAILED, attemptEnd, null);
    }

    public static WorkerTaskResult success(ExecutorContext.ExecutorWorkerTask emitted, Instant attemptEnd) {
        return terminated(emitted, State.Type.SUCCESS, attemptEnd, null);
    }

    public static WorkerTaskResult success(ExecutorContext.ExecutorWorkerTask emitted, Instant attemptEnd, Map<String, Object> outputs) {
        return terminated(emitted, State.Type.SUCCESS, attemptEnd, outputs);
    }

    private static WorkerTaskResult terminated(ExecutorContext.ExecutorWorkerTask emitted, State.Type state, Instant attemptEnd, Map<String, Object> outputs) {
        TaskRun taskRun = emitted.workerTask().getTaskRun();

        TaskRunAttempt attempt = TaskRunAttempt.builder()
            .state(new State(state, List.of(
                new State.History(State.Type.CREATED, attemptEnd.minusSeconds(1)),
                new State.History(state, attemptEnd)
            )))
            .build();

        TaskRun terminated = taskRun
            .withAttempts(List.of(attempt))
            .withState(state);

        return outputs == null ? new WorkerTaskResult(terminated) : new WorkerTaskResult(terminated, outputs);
    }
}
