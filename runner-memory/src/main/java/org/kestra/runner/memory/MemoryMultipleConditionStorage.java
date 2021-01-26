package org.kestra.runner.memory;

import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.models.triggers.multipleflows.TriggerExecutionWindow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;

@Singleton
public class MemoryMultipleConditionStorage implements MultipleConditionStorageInterface {
    private final Map<String, Map<String, Map<String, TriggerExecutionWindow>>> map = new ConcurrentHashMap<>();

    private Optional<Map<String, TriggerExecutionWindow>> find(String namepace, String flow) {
        if (!map.containsKey(namepace)) {
            return Optional.empty();
        }

        Map<String, Map<String, TriggerExecutionWindow>> byFlows = map.get(namepace);

        if (!byFlows.containsKey(flow)) {
            return Optional.empty();
        }

        return Optional.of(byFlows.get(flow));
    }

    @Override
    public Optional<TriggerExecutionWindow> get(Flow flow, String conditionId) {
        return find(flow.getNamespace(), flow.getId())
            .flatMap(byCondition -> byCondition.containsKey(conditionId) ?
                Optional.of(byCondition.get(conditionId)) :
                Optional.empty());
    }

    public synchronized void save(List<TriggerExecutionWindow> triggerExecutionWindows) {
        triggerExecutionWindows
            .forEach(window -> {
                if (!map.containsKey(window.getNamespace())) {
                    map.put(window.getNamespace(), new HashMap<>());
                }

                Map<String, Map<String, TriggerExecutionWindow>> byFlows = map.get(window.getNamespace());

                if (!map.containsKey(window.getFlowId())) {
                    byFlows.put(window.getFlowId(), new HashMap<>());
                }

                Map<String, TriggerExecutionWindow> byCondition = byFlows.get(window.getFlowId());

                byCondition.put(window.getConditionId(), window);
            });
    }

    public void delete(TriggerExecutionWindow triggerExecutionWindow) {
        find(triggerExecutionWindow.getNamespace(), triggerExecutionWindow.getFlowId())
            .ifPresent(byCondition -> {
                byCondition.remove(triggerExecutionWindow.getConditionId());
            });
    }
}
