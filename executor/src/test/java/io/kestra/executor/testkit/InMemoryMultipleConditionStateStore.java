package io.kestra.executor.testkit;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.TransactionContext;

/**
 * In-memory {@link MultipleConditionStateStore}: a synchronized map keyed by the window's
 * {@code uid()} (tenant|namespace|flowId|conditionId). {@code process} mirrors
 * {@code AbstractJdbcMultipleConditionStateStore}: get-or-create the window, run the consumer in
 * the "transaction", then — when the consumer produced an execution and {@code resetOnSuccess} is
 * not disabled — re-fetch and delete the window once all its conditions are satisfied.
 */
public class InMemoryMultipleConditionStateStore implements MultipleConditionStateStore {
    private final Map<String, MultipleConditionWindow> windows = new LinkedHashMap<>();

    @Override
    public synchronized Optional<MultipleConditionWindow> get(FlowId flow, String conditionId) {
        return Optional.ofNullable(windows.get(MultipleConditionWindow.uid(flow, conditionId)));
    }

    @Override
    public synchronized List<MultipleConditionWindow> expired(String tenantId) {
        ZonedDateTime now = ZonedDateTime.now();
        return windows.values().stream()
            .filter(window -> window.getTenantId() == null ? tenantId == null : window.getTenantId().equals(tenantId))
            .filter(window -> !window.isValid(now))
            .toList();
    }

    @Override
    public synchronized Execution process(FlowId flow, MultipleCondition multipleCondition, Map<String, Object> outputs,
        BiFunction<TransactionContext, MultipleConditionWindow, Execution> consumer) {
        MultipleConditionWindow window = get(flow, multipleCondition.getId())
            .orElseGet(() ->
            {
                MultipleConditionWindow created = create(flow, multipleCondition, outputs);
                windows.put(created.uid(), created);
                return created;
            });

        Execution newExecution = consumer.apply(NoopTransactionContext.INSTANCE, window);

        if (newExecution != null && !Boolean.FALSE.equals(multipleCondition.getResetOnSuccess())) {
            get(flow, multipleCondition.getId())
                .filter(multipleCondition::isConditionSatisfied)
                .ifPresent(this::delete);
        }

        return newExecution;
    }

    @Override
    public synchronized void save(TransactionContext txContext, MultipleConditionWindow multipleConditionWindow) {
        save(multipleConditionWindow);
    }

    @Override
    public synchronized void save(MultipleConditionWindow multipleConditionWindow) {
        windows.put(multipleConditionWindow.uid(), multipleConditionWindow);
    }

    @Override
    public synchronized void delete(MultipleConditionWindow multipleConditionWindow) {
        windows.remove(multipleConditionWindow.uid());
    }

    /** All windows currently persisted — assertion helper. */
    public synchronized List<MultipleConditionWindow> all() {
        return List.copyOf(windows.values());
    }
}
