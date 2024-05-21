package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.MapUtils;
import io.micronaut.core.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.N;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import jakarta.validation.ConstraintViolationException;

@Singleton
@Slf4j
public class PluginDefaultService {
    private static final ObjectMapper NON_DEFAULT_OBJECT_MAPPER = JacksonMapper.ofYaml()
        .copy()
        .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

    @Nullable
    @Inject
    protected TaskGlobalDefaultConfiguration taskGlobalDefault;

    @Nullable
    @Inject
    protected PluginGlobalDefaultConfiguration pluginGlobalDefault;

    @Inject
    protected YamlFlowParser yamlFlowParser;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    @Nullable
    protected QueueInterface<LogEntry> logQueue;

    @Inject
    private PluginRegistry pluginRegistry;

    private AtomicBoolean warnOnce = new AtomicBoolean(false);

    /**
     * @param flow the flow to extract default
     * @return list of {@code PluginDefault} ordered by most important first
     */
    protected List<PluginDefault> mergeAllDefaults(Flow flow) {
        List<PluginDefault> list = new ArrayList<>();

        if (taskGlobalDefault != null && taskGlobalDefault.getDefaults() != null) {
            if (warnOnce.compareAndSet(false, true)) {
                log.warn("Global Task Defaults are deprecated, please use Global Plugin Defaults instead via the 'kestra.plugins.defaults' property.");
            }
            list.addAll(taskGlobalDefault.getDefaults());
        }

        if (pluginGlobalDefault != null && pluginGlobalDefault.getDefaults() != null) {
            list.addAll(pluginGlobalDefault.getDefaults());
        }

        if (flow.getPluginDefaults() != null) {
            list.addAll(flow.getPluginDefaults());
        }

        return list;
    }

    private Map<String, List<PluginDefault>> pluginDefaultsToMap(List<PluginDefault> pluginDefaults) {
        return pluginDefaults
            .stream()
            .collect(Collectors.groupingBy(PluginDefault::getType));
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

        List<PluginDefault> allDefaults = mergeAllDefaults(flow);
        addAliases(allDefaults);
        Map<Boolean, List<PluginDefault>> allDefaultsGroup = allDefaults
            .stream()
            .collect(Collectors.groupingBy(PluginDefault::isForced, Collectors.toList()));

        // non forced
        Map<String, List<PluginDefault>> defaults = pluginDefaultsToMap(allDefaultsGroup.getOrDefault(false, Collections.emptyList()));

        // forced plugin default need to be reverse, lower win
        Map<String, List<PluginDefault>> forced = pluginDefaultsToMap(Lists.reverse(allDefaultsGroup.getOrDefault(true, Collections.emptyList())));

        Object pluginDefaults = flowAsMap.get("pluginDefaults");
        if (pluginDefaults != null) {
            flowAsMap.remove("pluginDefaults");
        }

        // we apply default and overwrite with forced
        if (!defaults.isEmpty()) {
            flowAsMap = (Map<String, Object>) recursiveDefaults(flowAsMap, defaults);
        }

        if (!forced.isEmpty()) {
            flowAsMap = (Map<String, Object>) recursiveDefaults(flowAsMap, forced);
        }

        if (pluginDefaults != null) {
            flowAsMap.put("pluginDefaults", pluginDefaults);
        }

        return yamlFlowParser.parse(flowAsMap, Flow.class, false);
    }

    private void addAliases(List<PluginDefault> allDefaults) {
        List<PluginDefault> aliasedPluginDefault = allDefaults.stream()
            .map(pluginDefault -> {
                Class<? extends Plugin> classByIdentifier = pluginRegistry.findClassByIdentifier(pluginDefault.getType());
                return classByIdentifier != null && !pluginDefault.getType().equals(classByIdentifier.getTypeName()) ? pluginDefault.toBuilder().type(classByIdentifier.getTypeName()).build() : null;
            })
            .filter(Objects::nonNull)
            .toList();

        allDefaults.addAll(aliasedPluginDefault);
    }

    private Object recursiveDefaults(Object object, Map<String, List<PluginDefault>> defaults) {
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
    private Map<?, ?> defaults(Map<?, ?> plugin, Map<String, List<PluginDefault>> defaults) {
        Object type = plugin.get("type");
        if (!(type instanceof String pluginType)) {
            return plugin;
        }

        List<PluginDefault> matching = defaults.entrySet()
            .stream()
            .filter(e -> e.getKey().equals(pluginType) || pluginType.startsWith(e.getKey()))
            .flatMap(e -> e.getValue().stream())
            .toList();

        if (matching.isEmpty()) {
            return plugin;
        }

        Map<String, Object> result = (Map<String, Object>) plugin;

        for (PluginDefault pluginDefault : matching) {
            if (pluginDefault.isForced()) {
                result = MapUtils.merge(result, pluginDefault.getValues());
            } else {
                result = MapUtils.merge(pluginDefault.getValues(), result);
            }
        }

        return result;
    }
}
