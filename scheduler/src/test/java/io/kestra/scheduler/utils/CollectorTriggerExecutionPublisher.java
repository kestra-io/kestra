package io.kestra.scheduler.utils;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.scheduler.service.TriggerExecutionPublisher;

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
