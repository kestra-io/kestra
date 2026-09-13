package io.kestra.core.models.triggers.multipleflows;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.runners.TransactionContext;

public interface MultipleConditionStateStore {
    Optional<MultipleConditionWindow> get(FlowId flow, String conditionId);

    List<MultipleConditionWindow> expired(String tenantId);

    Execution process(FlowId flow, MultipleCondition multipleCondition, Map<String, Object> outputs, BiFunction<TransactionContext, MultipleConditionWindow, Execution> consumer);

    default MultipleConditionWindow create(FlowId flow, MultipleCondition multipleCondition, Map<String, Object> outputs) {
        TimeWindow timeWindow = multipleCondition.getTimeWindow() != null ? multipleCondition.getTimeWindow() : TimeWindow.builder().build();

        // boundaries() re-expresses `now` in timeWindow.zoneId() regardless of the zone passed in here
        var startAndEnd = timeWindow.boundaries(ZonedDateTime.now().withNano(0));

        return MultipleConditionWindow.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .tenantId(flow.getTenantId())
            .conditionId(multipleCondition.getId())
            .start(startAndEnd.getLeft())
            .end(startAndEnd.getRight())
            .results(new HashMap<>())
            .outputs(outputs)
            .build();
    }

    void save(TransactionContext txContext, MultipleConditionWindow multipleConditionWindow);

    @VisibleForTesting
    void save(MultipleConditionWindow multipleConditionWindow);

    void delete(MultipleConditionWindow multipleConditionWindow);
}
