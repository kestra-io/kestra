package io.kestra.executor.testkit;

import java.time.Instant;

import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;

/**
 * Stands in for the worker in a closed-loop run: the executor emitted a {@link WorkerTask}, the
 * script decides what result comes back. No task actually runs.
 */
@FunctionalInterface
public interface ScriptedWorker {
    WorkerTaskResult run(WorkerTask task);

    /** Every task succeeds, with the given attempt end so retry arithmetic stays deterministic. */
    static ScriptedWorker succeeding(Instant attemptEnd) {
        return task -> Results.success(task, attemptEnd);
    }

    /** The task with {@code taskId} fails, everything else succeeds. */
    static ScriptedWorker failing(String taskId, Instant attemptEnd) {
        return task -> taskId.equals(task.getTaskRun().getTaskId()) ? Results.failed(task, attemptEnd) : Results.success(task, attemptEnd);
    }
}
