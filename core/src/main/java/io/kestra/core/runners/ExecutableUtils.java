package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;

import java.util.Collections;

public final class ExecutableUtils {

    private ExecutableUtils() {
        // prevent initialization
    }

    public static State.Type guessState(Execution execution, boolean transmitFailed) {
        if (transmitFailed &&
            (execution.getState().isFailed() || execution.getState().isPaused() || execution.getState().getCurrent() == State.Type.KILLED || execution.getState().getCurrent() == State.Type.WARNING)
        ) {
            return execution.getState().getCurrent();
        } else {
            return State.Type.SUCCESS;
        }
    }

    public static WorkerTaskResult workerTaskResult(TaskRun taskRun) {
        return WorkerTaskResult.builder()
            .taskRun(taskRun.withAttempts(
                Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(taskRun.getState().getCurrent())).build())
            ))
            .build();
    }
}
