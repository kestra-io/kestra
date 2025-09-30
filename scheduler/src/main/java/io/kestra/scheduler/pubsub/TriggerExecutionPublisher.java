package io.kestra.scheduler.pubsub;

import io.kestra.core.models.executions.Execution;

public interface TriggerExecutionPublisher {
    
    void send(final Execution execution);
}
