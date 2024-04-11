package io.kestra.core.server;

/**
 * The supported strategies for restarting tasks on worker failure.
 */
public enum WorkerTaskRestartStrategy {

    /**
     * Tasks are never restarted on worker failure.
     */
    NEVER,
    /**
     * Tasks are restarted immediately on worker failure, i.e., as soon as a worker id detected as disconnected.
     * This strategy is used to reduce task recovery times at the risk of introducing duplicate executions.
     */
    IMMEDIATELY,
    /**
     * Tasks are restarted on worker failure after the termination grace period is elapsed.
     * This strategy is used to limit the risk of task duplication.
     */
    AFTER_TERMINATION_GRACE_PERIOD;

    /**
     * Checks whether tasks are restartable for that strategy.
     *
     * @return {@code true} if tasks are restartable.
     */
    public boolean isRestartable() {
        return this.equals(IMMEDIATELY) || this.equals(AFTER_TERMINATION_GRACE_PERIOD);
    }

}
