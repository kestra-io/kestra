package io.kestra.plugin.flink.models;

/**
 * Represents the state of a Flink job.
 */
public enum JobState {
    INITIALIZING,
    CREATED,
    RUNNING,
    FAILING,
    FAILED,
    CANCELLING,
    CANCELED,
    FINISHED,
    RESTARTING,
    SUSPENDED,
    RECONCILING;
    
    /**
     * Check if the job state is terminal (won't change anymore)
     */
    public boolean isTerminal() {
        return this == FAILED || this == CANCELED || this == FINISHED;
    }
    
    /**
     * Check if the job state represents a successful completion
     */
    public boolean isSuccessful() {
        return this == FINISHED;
    }
    
    /**
     * Check if the job state represents a failure
     */
    public boolean isFailed() {
        return this == FAILED || this == CANCELED;
    }
    
    /**
     * Check if the job is currently running
     */
    public boolean isRunning() {
        return this == RUNNING;
    }
}