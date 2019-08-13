package org.floworc.core.queues;

public enum QueueName {
    WORKERS,
    EXECUTIONS,
    WORKERS_RESULT;

    public boolean isPubSub() {
        return this == EXECUTIONS;
    }
}
