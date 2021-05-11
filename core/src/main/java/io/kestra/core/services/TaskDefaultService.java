package io.kestra.core.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.TaskDefault;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.MapUtils;
import io.micronaut.core.annotation.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TaskDefaultService {
    @Nullable
    @Inject
    protected TaskGlobalDefaultConfiguration globalDefault;

    /**
     * @param flow the flow to extract default
     * @return list of {@code TaskDefault} order by most important first
     */
    protected List<TaskDefault> mergeAllDefaults(Flow flow) {
        List<TaskDefault> list = new ArrayList<>();

        if (globalDefault != null && globalDefault.getDefaults() != null) {
            list.addAll(globalDefault.getDefaults());
        }

        if (flow.getTaskDefaults() != null) {
            list.addAll(flow.getTaskDefaults());
        }

        return list;
    }

    private static Map<String, Map<String, Object>> taskDefaultsToMap(List<TaskDefault> taskDefaults) {
        return taskDefaults
            .stream()
            .map(taskDefault -> new AbstractMap.SimpleEntry<>(
                taskDefault.getType(),
                taskDefault.getValues()
            ))
            .collect(Collectors.toMap(
                AbstractMap.SimpleEntry::getKey,
                AbstractMap.SimpleEntry::getValue,
                MapUtils::merge
            ));
    }

    @SuppressWarnings("unchecked")
    public Flow injectDefaults(Flow flow) {
        Map<String, Object> flowAsMap = JacksonMapper.toMap(flow);

        Map<String, Map<String, Object>> defaults = taskDefaultsToMap(mergeAllDefaults(flow));

        Object taskDefaults = flowAsMap.get("taskDefaults");
        if (taskDefaults != null) {
            flowAsMap.remove("taskDefaults");
        }

        Map<String, Object> flowAsMapWithDefault = (Map<String, Object>) recursiveDefaults(flowAsMap, defaults);

        if (taskDefaults != null) {
            flowAsMapWithDefault.put("taskDefaults", taskDefaults);
        }

        return JacksonMapper.toMap(flowAsMapWithDefault, Flow.class);
    }

    private static Object recursiveDefaults(Object object, Map<String, Map<String, Object>> defaults) {
        if (object instanceof Map) {
            Map<?, ?> value = (Map<?, ?>) object;
            if (value.containsKey("type")) {
                value = defaults(value, defaults);
            }

            return value
                .entrySet()
                .stream()
                .map(e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    recursiveDefaults(e.getValue(), defaults)
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else if (object instanceof Collection) {
            Collection<?> value = (Collection<?>) object;
            return value
                .stream()
                .map(r -> recursiveDefaults(r, defaults))
                .collect(Collectors.toList());
        } else {
            return object;
        }
    }

    @SuppressWarnings("unchecked")
    protected static Map<?, ?> defaults(Map<?, ?> task, Map<String, Map<String, Object>> defaults) {
        Object type = task.get("type");
        if (!(type instanceof String)) {
            return task;
        }

        String taskType = (String) type;

        if (!defaults.containsKey(taskType)) {
            return task;
        }

        return MapUtils.merge(defaults.get(taskType), (Map<String, Object>) task);
    }
}
