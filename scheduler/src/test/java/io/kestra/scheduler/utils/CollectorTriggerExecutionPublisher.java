package io.kestra.scheduler.utils;

import io.kestra.core.models.executions.Execution;
import io.kestra.scheduler.pubsub.TriggerExecutionPublisher;

import java.util.ArrayList;
import java.util.List;

public class CollectorTriggerExecutionPublisher implements TriggerExecutionPublisher {
    
    List<Execution> executions = new ArrayList<>();
    
    @Override
    public void send(Execution execution) {
        this.executions.add(execution);
    }
    
    public List<Execution> executions() {
        return executions;
    }
    
    public void clear() {
        this.executions.clear();
    }
}
