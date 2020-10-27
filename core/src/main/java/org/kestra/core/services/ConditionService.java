package org.kestra.core.services;

import com.cronutils.utils.VisibleForTesting;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.conditions.ConditionContext;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.triggers.AbstractTrigger;
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

    public boolean isValid(AbstractTrigger trigger, Flow flow) {
        return this.isValid(trigger, flow, null);
    }

    @VisibleForTesting
    public boolean isValid(Condition condition, Flow flow, @Nullable Execution execution) {
        return this.valid(Collections.singletonList(condition), flow, execution);
    }

    public boolean isValid(AbstractTrigger trigger, Flow flow, @Nullable Execution execution) {
        List<Condition> conditions = trigger.getConditions() == null ? new ArrayList<>() : trigger.getConditions();

        return this.valid(conditions, flow, execution);
    }

    public boolean valid(List<Condition> list, Flow flow, @Nullable Execution execution) {
        ConditionContext conditionContext = ConditionContext.builder()
            .flow(flow)
            .execution(execution)
            .runContext(runContextFactory.of(flow, execution))
            .build();

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

        return flow
            .getListeners()
            .stream()
            .filter(listener -> listener.getConditions() == null ||
                this.valid(listener.getConditions(), flow, execution)
            )
            .flatMap(listener -> listener.getTasks().stream())
            .map(ResolvedTask::of)
            .collect(Collectors.toList());
    }
}
