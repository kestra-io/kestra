package io.kestra.core.runners;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;

import java.util.Map;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RunContextFactory {
    @Inject
    private ApplicationContext applicationContext;

    public RunContext of(Flow flow, Execution execution) {
        return new RunContext(applicationContext, flow, execution);
    }

    public RunContext of(Flow flow, Task task, Execution execution, TaskRun taskRun) {
        return this.of(flow, task, execution, taskRun, true);
    }

    public RunContext of(Flow flow, Task task, Execution execution, TaskRun taskRun, boolean decryptVariables) {
        return new RunContext(applicationContext, flow, task, execution, taskRun, decryptVariables);
    }

    public RunContext of(Flow flow, AbstractTrigger trigger) {
        return new RunContext(applicationContext, flow, trigger);
    }

    @VisibleForTesting
    public RunContext of(Map<String, Object> variables) {
        return new RunContext(applicationContext, variables);
    }

    @VisibleForTesting
    public RunContext of() {
        return new RunContext(applicationContext, ImmutableMap.of());
    }
}
