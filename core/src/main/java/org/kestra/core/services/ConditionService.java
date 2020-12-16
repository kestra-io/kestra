package org.kestra.core.services;

import com.cronutils.utils.VisibleForTesting;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunContextFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Provides business logic to manipulate {@link Condition}
 */
public class ConditionService {
    @Inject
    private RunContextFactory runContextFactory;

    @VisibleForTesting
    public boolean isValid(Condition condition, Flow flow, @Nullable Execution execution) {
        ConditionContext conditionContext = this.conditionContext(
            runContextFactory.of(flow, execution),
            flow,
            execution
        );

        return this.valid(Collections.singletonList(condition), conditionContext);
    }

    public boolean isValid(AbstractTrigger trigger, ConditionContext conditionContext) {
        List<Condition> conditions = trigger.getConditions() == null ? new ArrayList<>() : trigger.getConditions();

        return this.valid(conditions, conditionContext);
    }

    public boolean isValid(AbstractTrigger trigger, Flow flow, Execution execution) {
        assert execution != null;

        List<Condition> conditions = trigger.getConditions() == null ? new ArrayList<>() : trigger.getConditions();

        ConditionContext conditionContext = this.conditionContext(
            runContextFactory.of(flow, execution),
            flow,
            execution
        );

        return this.valid(conditions, conditionContext);
    }

    public ConditionContext conditionContext(RunContext runContext, Flow flow, @Nullable Execution execution) {
        return ConditionContext.builder()
            .flow(flow)
            .execution(execution)
            .runContext(runContext)
            .build();
    }

    boolean valid(List<Condition> list, ConditionContext conditionContext) {
        return list
            .stream()
            .allMatch(condition -> condition.test(conditionContext));
    }

    public boolean isTerminatedWithListeners(Flow flow, Execution execution) {
        if (!execution.getState().isTerninated()) {
            return false;
        }

        return execution.isTerminated(this.findValidListeners(flow, execution));
    }

    public List<ResolvedTask> findValidListeners(Flow flow, Execution execution) {
        if (flow.getListeners() == null) {
            return new ArrayList<>();
        }

        ConditionContext conditionContext = this.conditionContext(
            runContextFactory.of(flow, execution),
            flow,
            execution
        );

        return flow
            .getListeners()
            .stream()
            .filter(listener -> listener.getConditions() == null ||
                this.valid(listener.getConditions(), conditionContext)
            )
            .flatMap(listener -> listener.getTasks().stream())
            .map(ResolvedTask::of)
            .collect(Collectors.toList());
    }
}
