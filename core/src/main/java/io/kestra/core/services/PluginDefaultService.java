package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.MapUtils;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofYaml().copy()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Nullable
    @Inject
    protected TaskGlobalDefaultConfiguration taskGlobalDefault;

    @Nullable
    @Inject
    protected PluginGlobalDefaultConfiguration pluginGlobalDefault;

    @Inject
    protected YamlParser yamlParser;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    @Nullable
    protected QueueInterface<LogEntry> logQueue;

    @Inject
    private PluginRegistry pluginRegistry;

    private final AtomicBoolean warnOnce = new AtomicBoolean(false);

    @PostConstruct
    void validateGlobalPluginDefault() {
        List<PluginDefault> mergedDefaults = new ArrayList<>();
        if (taskGlobalDefault != null && taskGlobalDefault.getDefaults() != null) {
            mergedDefaults.addAll(taskGlobalDefault.getDefaults());
        }

        if (pluginGlobalDefault != null && pluginGlobalDefault.getDefaults() != null) {
            mergedDefaults.addAll(pluginGlobalDefault.getDefaults());
        }

        mergedDefaults.stream()
            .flatMap(pluginDefault -> this.validateDefault(pluginDefault).stream())
            .forEach(violation -> log.error("Invalid plugin default configuration: {}", violation));
    }

    /**
     * @param flow the flow to extract default
     * @return list of {@code PluginDefault} ordered by most important first
     */
    protected List<PluginDefault> mergeAllDefaults(Flow flow) {
        List<PluginDefault> list = new ArrayList<>();

        if (flow.getPluginDefaults() != null) {
            list.addAll(flow.getPluginDefaults());
        }

        if (taskGlobalDefault != null && taskGlobalDefault.getDefaults() != null) {
            if (warnOnce.compareAndSet(false, true)) {
                log.warn("Global Task Defaults are deprecated, please use Global Plugin Defaults instead via the 'kestra.plugins.defaults' configuration property.");
            }
            list.addAll(taskGlobalDefault.getDefaults());
        }

        if (pluginGlobalDefault != null && pluginGlobalDefault.getDefaults() != null) {
            list.addAll(pluginGlobalDefault.getDefaults());
        }

        return list;
    }

    /**
     * Inject plugin defaults into a Flow.
     * In case of exception, the flow is returned as is,
     * then a logger is created based on the execution to be able to log an exception in the execution logs.
     */
    public FlowWithSource injectDefaults(FlowWithSource flow, Execution execution) {
        try {
            return this.injectDefaults(flow);
        } catch (Exception e) {
            RunContextLogger
                .logEntries(
                    Execution.loggingEventFromException(e),
                    LogEntry.of(execution)
                )
                .forEach(logEntry -> {
                    try {
                        logQueue.emitAsync(logEntry);
                    } catch (QueueException e1) {
                        // silently do nothing
                    }
                });
            return flow;
        }
    }

    /**
     * @deprecated use {@link #injectDefaults(FlowWithSource, Logger)} instead
     */
    @Deprecated(forRemoval = true, since = "0.20")
    public Flow injectDefaults(Flow flow, Logger logger) {
        try {
            return this.injectDefaults(flow);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            return flow;
        }
    }

    /**
     * Inject plugin defaults into a Flow.
     * In case of exception, the flow is returned as is, then the logger is used to log the exception.
     */
    public FlowWithSource injectDefaults(FlowWithSource flow, Logger logger) {
        try {
            return this.injectDefaults(flow);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            return flow;
        }
    }

    /**
     * @deprecated use {@link #injectDefaults(FlowWithSource)} instead
     */
    @Deprecated(forRemoval = true, since = "0.20")
    public Flow injectDefaults(Flow flow) throws ConstraintViolationException {
        if (flow instanceof FlowWithSource flowWithSource) {
            return this.injectDefaults(flowWithSource);
        }

        Map<String, Object> flowAsMap = NON_DEFAULT_OBJECT_MAPPER.convertValue(flow, JacksonMapper.MAP_TYPE_REFERENCE);

        return innerInjectDefault(flow, flowAsMap);
    }

    /**
     * Inject plugin defaults into a Flow.
     */
    public FlowWithSource injectDefaults(FlowWithSource flow) throws ConstraintViolationException {
        try {
            Map<String, Object> flowAsMap = OBJECT_MAPPER.readValue(flow.getSource(), JacksonMapper.MAP_TYPE_REFERENCE);

            Flow withDefault =  innerInjectDefault(flow, flowAsMap);

            // revision and tenants are not in the source, so we copy them manually
            return withDefault.toBuilder()
                .tenantId(flow.getTenantId())
                .revision(flow.getRevision())
                .build()
                .withSource(flow.getSource());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Flow innerInjectDefault(Flow flow, Map<String, Object> flowAsMap) {
        List<PluginDefault> allDefaults = mergeAllDefaults(flow);
        addAliases(allDefaults);
        Map<Boolean, List<PluginDefault>> allDefaultsGroup = allDefaults
            .stream()
            .collect(Collectors.groupingBy(PluginDefault::isForced, Collectors.toList()));

        // non-forced
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

        return yamlParser.parse(flowAsMap, Flow.class, false);
    }

    /**
     * Validate a plugin default by comparing its properties with the getters of the plugin class.
     * <p>
     * If the plugin default type is unknown,
     * validation will be disabled as we cannot differentiate between a prefix or an unknown type.
     */
    public List<String> validateDefault(PluginDefault pluginDefault) {
        Class<? extends Plugin> classByIdentifier = pluginRegistry.findClassByIdentifier(pluginDefault.getType());
        if (classByIdentifier == null) {
            // this can either be a prefix or a non-existing plugin, in both cases we cannot validate in detail
            return Collections.emptyList();
        }

        Set<String> pluginDefaultProperties = pluginDefault.getValues().keySet();
        List<String> pluginProperties = Stream.of(classByIdentifier.getMethods())
            .filter(method -> method.getName().startsWith("get") || method.getName().startsWith("is"))
            .map(method -> {
                if (method.getName().startsWith("get")) {
                    return method.getName().substring(3).toLowerCase();
                }
                return method.getName().substring(2).toLowerCase();
            })
            .toList();

        return pluginDefaultProperties.stream()
            .filter(property -> !pluginProperties.contains(property.toLowerCase()))
            .map(property -> "No property '" + property + "' exists in plugin '" + pluginDefault.getType() + "'")
            .toList();
    }

    private Map<String, List<PluginDefault>> pluginDefaultsToMap(List<PluginDefault> pluginDefaults) {
        return pluginDefaults
            .stream()
            .collect(Collectors.groupingBy(PluginDefault::getType));
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

    @VisibleForTesting
    Object recursiveDefaults(Object object, Map<String, List<PluginDefault>> defaults) {
        if (object instanceof Map<?, ?> value) {
            value = value
                .entrySet()
                .stream()
                .map(e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    recursiveDefaults(e.getValue(), defaults)
                ))
                .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);

            if (value.containsKey("type")) {
                value = defaults(value, defaults);
            }

            return value;
        } else if (object instanceof Collection<?> value) {
            return value
                .stream()
                .map(r -> recursiveDefaults(r, defaults))
                .toList();
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
