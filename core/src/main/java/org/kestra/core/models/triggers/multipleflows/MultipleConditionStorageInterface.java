package org.kestra.core.models.triggers.multipleflows;

import org.kestra.core.models.conditions.types.MultipleCondition;
import org.kestra.core.models.flows.Flow;

import java.util.HashMap;
import java.util.Optional;

public interface MultipleConditionStorageInterface {
    Optional<TriggerExecutionWindow> get(Flow flow, String conditionId);

    default TriggerExecutionWindow getOrCreate(Flow flow, MultipleCondition multipleCondition) {
        return this.get(flow, multipleCondition.getId())
            .orElseGet(() -> TriggerExecutionWindow.builder()
                .namespace(flow.getNamespace())
                .flowId(flow.getId())
                .conditionId(multipleCondition.getId())
//                .start()
//                .end()
                .results(new HashMap<>())
                .build()
            );
    }
}
