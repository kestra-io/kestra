package org.kestra.core.runners;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.tasks.ResolvedTask;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RunContextFactory {
    @Inject
    private ApplicationContext applicationContext;

    public RunContext of(Flow flow, Execution execution) {
        return new RunContext(applicationContext, flow, execution);
    }

    public RunContext of(Flow flow, ResolvedTask task, Execution execution, TaskRun taskRun) {
        return new RunContext(applicationContext, flow, task, execution, taskRun);
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
