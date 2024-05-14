package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.TaskDefault;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.MapUtils;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import jakarta.validation.ConstraintViolationException;

@Singleton
public class TaskDefaultService {
    private static final ObjectMapper NON_DEFAULT_OBJECT_MAPPER = JacksonMapper.ofYaml()
        .copy()
        .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

    @Nullable
    @Inject
    protected TaskGlobalDefaultConfiguration globalDefault;

    @Inject
    protected YamlFlowParser yamlFlowParser;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    @Nullable
    protected QueueInterface<LogEntry> logQueue;

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

    private Map<String, List<TaskDefault>> taskDefaultsToMap(List<TaskDefault> taskDefaults) {
        return taskDefaults
            .stream()
            .collect(Collectors.groupingBy(TaskDefault::getType));
    }

    public Flow injectDefaults(Flow flow, Execution execution) {
        try {
            return this.injectDefaults(flow);
        } catch (Exception e) {
            RunContextLogger
                .logEntries(
                    Execution.loggingEventFromException(e),
                    LogEntry.of(execution)
                )
                .forEach(logQueue::emitAsync);
            return flow;
        }
    }

    public Flow injectDefaults(Flow flow, Logger logger) {
        try {
            return this.injectDefaults(flow);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            return flow;
        }
    }

    @SuppressWarnings("unchecked")
    public Flow injectDefaults(Flow flow) throws ConstraintViolationException {
        if (flow instanceof FlowWithSource) {
            flow = ((FlowWithSource) flow).toFlow();
        }

        Map<String, Object> flowAsMap = NON_DEFAULT_OBJECT_MAPPER.convertValue(flow, JacksonMapper.MAP_TYPE_REFERENCE);

        List<TaskDefault> allDefaults = mergeAllDefaults(flow);
        Map<Boolean, List<TaskDefault>> allDefaultsGroup = allDefaults
            .stream()
            .collect(Collectors.groupingBy(TaskDefault::isForced, Collectors.toList()));

        // non forced
        Map<String, List<TaskDefault>> defaults = taskDefaultsToMap(allDefaultsGroup.getOrDefault(false, Collections.emptyList()));

        // forced task default need to be reverse, lower win
        Map<String, List<TaskDefault>> forced = taskDefaultsToMap(Lists.reverse(allDefaultsGroup.getOrDefault(true, Collections.emptyList())));

        Object taskDefaults = flowAsMap.get("taskDefaults");
        if (taskDefaults != null) {
            flowAsMap.remove("taskDefaults");
        }

        // we apply default and overwrite with forced
        if (!defaults.isEmpty()) {
            flowAsMap = (Map<String, Object>) recursiveDefaults(flowAsMap, defaults);
        }

        if (!forced.isEmpty()) {
            flowAsMap = (Map<String, Object>) recursiveDefaults(flowAsMap, forced);
        }

        if (taskDefaults != null) {
            flowAsMap.put("taskDefaults", taskDefaults);
        }

        return yamlFlowParser.parse(flowAsMap, Flow.class, false);
    }

    private Object recursiveDefaults(Object object, Map<String, List<TaskDefault>> defaults) {
        if (object instanceof Map<?, ?> value) {
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
                .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);
        } else if (object instanceof Collection<?> value) {
            return value
                .stream()
                .map(r -> recursiveDefaults(r, defaults))
                .collect(Collectors.toList());
        } else {
            return object;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> defaults(Map<?, ?> task, Map<String, List<TaskDefault>> defaults) {
        Object type = task.get("type");
        if (!(type instanceof String taskType)) {
            return task;
        }

        List<TaskDefault> matching = defaults.entrySet()
            .stream()
            .filter(e -> e.getKey().equals(taskType) || taskType.startsWith(e.getKey()))
            .flatMap(e -> e.getValue().stream())
            .toList();

        if (matching.isEmpty()) {
            return task;
        }

        Map<String, Object> result = (Map<String, Object>) task;

        for (TaskDefault taskDefault : matching) {
            if (taskDefault.isForced()) {
                result = MapUtils.merge(result, taskDefault.getValues());
            } else {
                result = MapUtils.merge(taskDefault.getValues(), result);
            }
        }

        return result;
    }
}
