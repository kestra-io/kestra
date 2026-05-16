package io.kestra.core.models.tasks;

/**
 * Strategy when no worker is available on the tag-matched Worker Queue.
 */
public enum WorkerQueueFallback {
    /** Fail the job. */
    FAIL,
    /** Hold the job until a worker becomes available. */
    WAIT,
    /** Cancel the job. */
    CANCEL,
    /** Drop the tag requirement and route to the default Worker Queue. */
    IGNORE
}
