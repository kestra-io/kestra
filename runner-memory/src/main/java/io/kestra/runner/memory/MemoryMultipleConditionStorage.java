package io.kestra.runner.memory;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MemoryMultipleConditionStorage implements MultipleConditionStorageInterface {
    private final Map<String, Map<String, Map<String, MultipleConditionWindow>>> map = new ConcurrentHashMap<>();

    private Optional<Map<String, MultipleConditionWindow>> find(String namepace, String flow) {
        if (!map.containsKey(namepace)) {
            return Optional.empty();
        }

        Map<String, Map<String, MultipleConditionWindow>> byFlows = map.get(namepace);

        if (!byFlows.containsKey(flow)) {
            return Optional.empty();
        }

        return Optional.of(byFlows.get(flow));
    }

    @Override
    public Optional<MultipleConditionWindow> get(Flow flow, String conditionId) {
        return find(flow.getNamespace(), flow.getId())
            .flatMap(byCondition -> byCondition.containsKey(conditionId) ?
                Optional.of(byCondition.get(conditionId)) :
                Optional.empty());
    }

    @Override
    public List<MultipleConditionWindow> expired(String tenantId) {
        ZonedDateTime now = ZonedDateTime.now();

        return map
            .entrySet()
            .stream()
            .flatMap(e -> e.getValue().entrySet().stream())
            .flatMap(e -> e.getValue().entrySet().stream())
            .map(Map.Entry::getValue)
            .filter(e -> !e.isValid(now))
            .collect(Collectors.toList());
    }

    @Override
    public synchronized void save(List<MultipleConditionWindow> multipleConditionWindows) {
        multipleConditionWindows
            .forEach(window -> {
                if (!map.containsKey(window.getNamespace())) {
                    map.put(window.getNamespace(), new HashMap<>());
                }

                Map<String, Map<String, MultipleConditionWindow>> byFlows = map.get(window.getNamespace());

                if (!map.containsKey(window.getFlowId())) {
                    byFlows.put(window.getFlowId(), new HashMap<>());
                }

                Map<String, MultipleConditionWindow> byCondition = byFlows.get(window.getFlowId());

                byCondition.put(window.getConditionId(), window);
            });
    }

    @Override
    public void delete(MultipleConditionWindow multipleConditionWindow) {
        find(multipleConditionWindow.getNamespace(), multipleConditionWindow.getFlowId())
            .ifPresent(byCondition -> {
                byCondition.remove(multipleConditionWindow.getConditionId());
            });
    }
}
