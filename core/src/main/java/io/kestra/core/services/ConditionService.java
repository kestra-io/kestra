package io.kestra.core.services;

import com.cronutils.utils.VisibleForTesting;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwPredicate;

/**
 * Provides business logic to manipulate {@link Condition}
 */
@Singleton
public class ConditionService {
    @Inject
    private RunContextFactory runContextFactory;

    @VisibleForTesting
    public boolean isValid(Condition condition, Flow flow, @Nullable Execution execution, MultipleConditionStorageInterface multipleConditionStorage) {
        ConditionContext conditionContext = this.conditionContext(
            runContextFactory.of(flow, execution),
            flow,
            execution,
            multipleConditionStorage
        );

        return this.valid(flow, Collections.singletonList(condition), conditionContext);
    }

    public boolean isValid(Condition condition, Flow flow, @Nullable Execution execution) {
        return this.isValid(condition, flow, execution, null);
    }

    private void logException(Flow flow, Object condition, ConditionContext conditionContext, Exception e) {
        conditionContext.getRunContext().logger().warn(
            "[namespace: {}] [flow: {}] [condition: {}] Evaluate Condition Failed with error '{}'",
            flow.getNamespace(),
            flow.getId(),
            condition.toString(),
            e.getMessage(),
            e
        );
    }

    public boolean isValid(Flow flow, AbstractTrigger trigger, ConditionContext conditionContext) {
        List<Condition> conditions = trigger.getConditions() == null ? new ArrayList<>() : trigger.getConditions();

        return this.valid(flow, conditions, conditionContext);
    }

    /**
     * Check that all conditions are valid.
     * Warning, this method throws if a condition cannot be evaluated.
     */
    public boolean isValid(List<ScheduleCondition> conditions, ConditionContext conditionContext) throws InternalException {
        return conditions
            .stream()
            .allMatch(throwPredicate(condition -> condition.test(conditionContext)));
    }

    /**
     * Check that all conditions are valid.
     * Warning, this method throws if a condition cannot be evaluated.
     */
    public boolean areValid(List<Condition> conditions, ConditionContext conditionContext) throws InternalException {
        return conditions
            .stream()
            .allMatch(throwPredicate(condition -> condition.test(conditionContext)));
    }

    public boolean isValid(AbstractTrigger trigger, Flow flow, Execution execution, MultipleConditionStorageInterface multipleConditionStorage) {
        assert execution != null;

        List<Condition> conditions = trigger.getConditions() == null ? new ArrayList<>() : trigger.getConditions();

        ConditionContext conditionContext = this.conditionContext(
            runContextFactory.of(flow, execution),
            flow,
            execution,
            multipleConditionStorage
        );

        return this.valid(flow, conditions, conditionContext);
    }

    public ConditionContext conditionContext(RunContext runContext, Flow flow, @Nullable Execution execution, MultipleConditionStorageInterface multipleConditionStorage) {
        return ConditionContext.builder()
            .flow(flow)
            .execution(execution)
            .runContext(runContext)
            .multipleConditionStorage(multipleConditionStorage)
            .build();
    }

    public ConditionContext conditionContext(RunContext runContext, Flow flow, @Nullable Execution execution) {
        return this.conditionContext(runContext, flow, execution, null);
    }

    boolean valid(Flow flow, List<Condition> list, ConditionContext conditionContext) {
        return list
            .stream()
            .allMatch(condition -> {
                try {
                    return condition.test(conditionContext);
                } catch (Exception e) {
                    logException(flow, condition, conditionContext, e);

                    return false;
                }
            });
    }

    public boolean isTerminatedWithListeners(Flow flow, Execution execution) {
        if (!execution.getState().isTerminated()) {
            return false;
        }

        return execution.isTerminated(this.findValidListeners(flow, execution));
    }

    @SuppressWarnings("deprecation")
    public List<ResolvedTask> findValidListeners(Flow flow, Execution execution) {
        if (flow == null || flow.getListeners() == null) {
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
                this.valid(flow, listener.getConditions(), conditionContext)
            )
            .flatMap(listener -> listener.getTasks().stream())
            .map(ResolvedTask::of)
            .toList();
    }
}
