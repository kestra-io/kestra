package io.kestra.core.services;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.Logs;
import io.kestra.core.utils.MapUtils;
import io.kestra.plugin.core.flow.Template;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses flows and injects plugin default values into every plugin (task, trigger, task runner, …).
 *
 * <h2>Sources &amp; precedence</h2>
 * Defaults come from three levels, ordered most-important-first: <b>flow</b> &gt; <b>namespace</b> (EE, closest
 * namespace first) &gt; <b>global</b> (configuration). Each default is either:
 * <ul>
 *   <li><b>type-matched</b> — applied to every plugin whose {@code type} equals or is prefixed by the default's
 *       {@code type}; or</li>
 *   <li><b>named</b> — carries a {@code ref} id and is applied <i>only</i> to plugins that opt in with
 *       {@code pluginDefaultsRef: <id>}, never by type matching.</li>
 * </ul>
 *
 * <h2>{@code forced}</h2>
 * {@code forced: true} marks an enforced default and is honored at every level (flow, namespace, global). A
 * non-forced default fills only values the plugin does not set (the plugin wins); a forced default overrides the
 * plugin's values. Forced defaults follow the <i>reverse</i> precedence of non-forced ones: the lowest (admin)
 * level wins, so a global forced default beats a namespace forced one beats a flow forced one and enforcement at a
 * higher (admin) level cannot be bypassed closer to the flow.
 *
 * <h2>How a single plugin is resolved</h2>
 * <ol>
 *   <li><b>No {@code pluginDefaultsRef}:</b> non-forced then forced type-matched defaults are applied (forced last,
 *       so it wins).</li>
 *   <li><b>With {@code pluginDefaultsRef: id}:</b> non-forced type-matched defaults are skipped. If a <i>forced</i>
 *       type-matched default also applies, <b>it takes over entirely and the referenced default is ignored</b>
 *       (no stacking) — enforcement always wins. Otherwise the referenced default is applied.</li>
 * </ol>
 *
 * <h2>Resolving a {@code ref}</h2>
 * All defaults sharing a {@code ref} collapse to a single winner (shadowing, not a merge): a forced entry beats a
 * non-forced one; among forced entries the lowest-priority (admin) level wins (global over namespace over flow) so
 * enforcement cannot be bypassed by a higher-level forced override; among non-forced entries the highest-priority
 * level wins. The referenced default is applied only when its declared {@code type} matches the plugin's type
 * (plugin aliases are canonicalized so an alias and its canonical name match).
 * <p>
 * Resulting precedence for a referenced plugin:
 * {@code forced type-matched > forced ref > non-forced ref > the plugin's own values}.
 *
 * <h2>Unknown / inapplicable {@code ref}</h2>
 * If a {@code pluginDefaultsRef} resolves to no default (none with that id) or to one declared for a different
 * type, it is left unresolved: flow validation reports it as an error on create/update and via the validate API,
 * and the plugin fails at runtime with {@code PluginDefaultsRefNotFoundException}. When a reference <i>is</i>
 * applied (or is validly superseded by a forced type default), its marker is consumed so a surviving marker
 * unambiguously means "unresolved or inapplicable".
 */
@Singleton
@Slf4j
public class PluginDefaultService {
    private static final ObjectMapper NON_DEFAULT_OBJECT_MAPPER = JacksonMapper.ofYaml()
        .copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);

    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofYaml().copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    private static final String PLUGIN_DEFAULTS_FIELD = "pluginDefaults";
    private static final String PLUGIN_DEFAULTS_REF_FIELD = "pluginDefaultsRef";
    private static final String TASK_DEFAULTS_FIELD = "taskDefaults";

    private static final TypeReference<List<PluginDefault>> PLUGIN_DEFAULTS_TYPE_REF = new TypeReference<>() {
    };

    @Nullable
    @Inject
    protected TaskGlobalDefaultConfiguration taskGlobalDefault;

    @Nullable
    @Inject
    protected PluginGlobalDefaultConfiguration pluginGlobalDefault;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    @Nullable
    protected QueueInterface<LogEntry> logQueue;

    @Inject
    protected PluginRegistry pluginRegistry;

    @Value("{kestra.templates.enabled:false}")
    private boolean templatesEnabled;

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
     * Gets all the defaults values for the given flow.
     *
     * @param flow the flow to extract default
     * @return list of {@code PluginDefault} ordered by most important first
     */
    protected List<PluginDefault> getAllDefaults(final String tenantId,
        final String namespace,
        final Map<String, Object> flow) {
        List<PluginDefault> defaults = new ArrayList<>();
        defaults.addAll(getFlowDefaults(flow));
        defaults.addAll(getGlobalDefaults());
        return defaults;
    }

    /**
     * Gets the flow-level defaults values.
     *
     * @param flow the flow to extract default
     * @return list of {@code PluginDefault} ordered by most important first
     */
    protected List<PluginDefault> getFlowDefaults(final Map<String, Object> flow) {
        Object defaults = flow.get(PLUGIN_DEFAULTS_FIELD);
        if (defaults == null) {
            // Fallback to the deprecated 'taskDefaults' field for backward compatibility.
            // The deprecation itself is surfaced to the UI via FlowService.deprecationPaths,
            // which relies on Flow.taskDefaults being populated by Jackson during deserialization.
            defaults = flow.get(TASK_DEFAULTS_FIELD);
        }
        if (defaults != null) {
            return OBJECT_MAPPER.convertValue(defaults, PLUGIN_DEFAULTS_TYPE_REF);
        } else {
            return List.of();
        }
    }

    /**
     * Gets the global defaults values.
     *
     * @return list of {@code PluginDefault} ordered by most important first
     */
    protected List<PluginDefault> getGlobalDefaults() {
        List<PluginDefault> defaults = new ArrayList<>();

        if (taskGlobalDefault != null && taskGlobalDefault.getDefaults() != null) {
            if (warnOnce.compareAndSet(false, true)) {
                log.warn("Global Task Defaults are deprecated, please use Global Plugin Defaults instead via the 'kestra.plugins.defaults' configuration property.");
            }
            defaults.addAll(taskGlobalDefault.getDefaults());
        }

        if (pluginGlobalDefault != null && pluginGlobalDefault.getDefaults() != null) {
            defaults.addAll(pluginGlobalDefault.getDefaults());
        }
        return defaults;
    }

    /**
     * Parses the given abstract flow and injects all default values, returning a parsed {@link FlowWithSource}.
     *
     * <p>
     * If an exception occurs during parsing, the original flow is returned unchanged, and the exception is logged
     * for the passed {@code execution}
     * </p>
     *
     * @return a parsed {@link FlowWithSource}, or a {@link FlowWithException} if parsing fails
     */
    public FlowWithSource injectDefaults(FlowInterface flow, Execution execution) {
        try {
            return this.injectAllDefaults(flow, false);
        } catch (Exception e) {
            try {
                logQueue.emitAsync(
                    RunContextLogger
                        .logEntries(
                            Execution.loggingEventFromException(e),
                            LogEntry.of(execution)
                        )
                );
            } catch (QueueException e1) {
                // silently do nothing
            }
            return readWithoutDefaultsOrThrow(flow);
        }
    }

    /**
     * Parses the given abstract flow and injects all default values, returning a parsed {@link FlowWithSource}.
     *
     * <p>
     * If an exception occurs during parsing, the original flow is returned unchanged, and the exception is logged.
     * </p>
     *
     * @return a parsed {@link FlowWithSource}, or a {@link FlowWithException} if parsing fails
     */
    public FlowWithSource injectAllDefaults(FlowInterface flow, Logger logger) {
        try {
            return this.injectAllDefaults(flow, false);
        } catch (Exception e) {
            logger.warn(
                "Can't inject plugin defaults on tenant {}, namespace '{}', flow '{}' with errors '{}'",
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getId(),
                e.getMessage(),
                e
            );
            return readWithoutDefaultsOrThrow(flow);
        }
    }

    private static FlowWithSource readWithoutDefaultsOrThrow(final FlowInterface flow) {
        if (flow instanceof FlowWithSource item) {
            return item;
        }

        if (flow instanceof Flow item) {
            return FlowWithSource.of(item, item.sourceOrGenerateIfNull());
        }

        // The block below should only be reached during testing for failure scenarios
        try {
            Flow parsed = NON_DEFAULT_OBJECT_MAPPER.readValue(flow.getSource(), Flow.class);
            return FlowWithSource.of(parsed, flow.getSource());
        } catch (JsonProcessingException e) {
            throw new KestraRuntimeException("Failed to read flow from source", e);
        }
    }

    /**
     * Parses the given abstract flow and injects all default values, returning a parsed {@link FlowWithSource}.
     *
     * <p>
     * If {@code strictParsing} is {@code true}, the parsing will fail in the following cases:
     * </p>
     * <ul>
     * <li>The source contains duplicate properties.</li>
     * <li>The source contains unknown properties.</li>
     * </ul>
     *
     * @param flow the flow to be parsed
     * @param strictParsing specifies if the source must meet strict validation requirements
     * @return a parsed {@link FlowWithSource}
     *
     * @throws FlowProcessingException if an error occurred while processing the flow
     */
    public FlowWithSource injectAllDefaults(final FlowInterface flow, final boolean strictParsing) throws FlowProcessingException {

        // Flow revisions created from older Kestra versions may not be linked to their original source.
        // In such cases, fall back to the generated source approach to enable plugin default injection.
        String source = flow.sourceOrGenerateIfNull();

        if (source == null) {
            // This should never happen
            String error = "Cannot apply plugin defaults. Cause: flow has no defined source.";
            Logs.logExecution(flow, log, Level.ERROR, error);
            throw new IllegalArgumentException(error);
        }

        try {
            return parseFlowWithAllDefaults(
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getRevision(),
                flow.isDeleted(),
                source,
                false,
                strictParsing
            );
        } catch (ConstraintViolationException e) {
            throw new FlowProcessingException(e);
        } catch (JsonProcessingException e) {
            throw new FlowProcessingException(YamlParser.toConstraintViolationException(source, "Flow", e));
        }
    }

    /**
     * Parses the given abstract flow and injects default plugin versions, returning a parsed {@link FlowWithSource}.
     *
     * <p>
     * If the provided flow already represents a concrete {@link FlowWithSource}, it is returned as is.
     * <p/>
     *
     * <p>
     * If {@code safe} is set to {@code true} and the given flow cannot be parsed,
     * this method returns a {@link FlowWithException} instead of throwing an error.
     * <p/>
     *
     * @param flow the flow to be parsed
     * @param safe whether parsing errors should be handled gracefully
     * @param strictParsing determine strictness of flow source parsing
     * @return a parsed {@link FlowWithSource}, or a {@link FlowWithException} if parsing fails and {@code safe} is {@code true}
     *
     * @throws FlowProcessingException if an error occurred while processing the flow and {@code safe} is {@code false}.
     */
    public FlowWithSource injectVersionDefaults(final FlowInterface flow, final boolean safe, final boolean strictParsing) throws FlowProcessingException {
        if (flow instanceof FlowWithSource flowWithSource) {
            // shortcut - if the flow is already fully parsed return it immediately.
            return flowWithSource;
        }

        FlowWithSource result;

        try {
            String source = flow.getSource();
            if (source == null) {
                source = OBJECT_MAPPER.writeValueAsString(flow);
            }

            result = parseFlowWithAllDefaults(flow.getTenantId(), flow.getNamespace(), flow.getRevision(), flow.isDeleted(), source, true, strictParsing);
        } catch (Exception e) {
            if (safe) {
                Logs.logExecution(flow, log, Level.ERROR, "Failed to read flow.", e);
                result = FlowWithException.from(flow, e);

                // deleted is not part of the original 'source'
                result = result.toBuilder().deleted(flow.isDeleted()).build();
            } else {
                throw new FlowProcessingException(e);
            }
        }
        return result;
    }

    public FlowWithSource injectVersionDefaults(final FlowInterface flow, final boolean safe) throws FlowProcessingException {
        return injectVersionDefaults(flow, safe, false);
    }

    public Map<String, Object> injectVersionDefaults(@Nullable final String tenantId,
        final String namespace,
        final Map<String, Object> mapFlow) throws FlowProcessingException {
        return innerInjectDefault(tenantId, namespace, mapFlow, true);
    }

    /**
     * Parses and injects default into the given flow.
     *
     * @param tenantId the Tenant ID.
     * @param source the flow source.
     * @return a new {@link FlowWithSource}.
     *
     * @throws FlowProcessingException when parsing flow.
     */
    public FlowWithSource parseFlowWithAllDefaults(@Nullable final String tenantId, final String source, final boolean strict) throws FlowProcessingException {
        try {
            return parseFlowWithAllDefaults(tenantId, null, null, false, source, false, strict);
        } catch (ConstraintViolationException e) {
            throw new FlowProcessingException(e);
        } catch (JsonProcessingException e) {
            throw new FlowProcessingException(YamlParser.toConstraintViolationException(source, "Flow", e));
        }
    }

    /**
     * Parses and injects plugin default versions into the given flow.
     *
     * @param tenantId the Tenant ID.
     * @param source the flow source.
     * @param strictParsing specifies if the source must meet strict validation requirements
     * @return a new {@link FlowWithSource}.
     *
     * @throws FlowProcessingException when parsing flow.
     */
    public FlowWithSource parseFlowWithVersionDefaults(@Nullable final String tenantId, final String source, final boolean strictParsing) throws FlowProcessingException {
        try {
            return parseFlowWithAllDefaults(tenantId, null, null, false, source, true, strictParsing);
        } catch (ConstraintViolationException e) {
            throw new FlowProcessingException(e);
        } catch (JsonProcessingException e) {
            throw new FlowProcessingException(YamlParser.toConstraintViolationException(source, "Flow", e));
        }
    }

    /**
     * Parses and injects defaults into the given flow.
     *
     * @param tenant the tenant identifier.
     * @param namespace the namespace.
     * @param revision the flow revision.
     * @param source the flow source.
     * @return a new {@link FlowWithSource}.
     *
     * @throws ConstraintViolationException when parsing flow.
     */
    private FlowWithSource parseFlowWithAllDefaults(@Nullable final String tenant,
        @Nullable String namespace,
        @Nullable Integer revision,
        final boolean isDeleted,
        final String source,
        final boolean onlyVersions,
        final boolean strictParsing) throws ConstraintViolationException, JsonProcessingException {
        Map<String, Object> mapFlow = OBJECT_MAPPER.readValue(source, JacksonMapper.MAP_TYPE_REFERENCE);
        namespace = namespace == null ? (String) mapFlow.get("namespace") : namespace;
        revision = revision == null ? (Integer) mapFlow.get("revision") : revision;

        mapFlow = innerInjectDefault(tenant, namespace, mapFlow, onlyVersions);

        FlowWithSource withDefault = YamlParser.parse(mapFlow, FlowWithSource.class, strictParsing);

        // revision, tenants, and deleted are not in the 'source', so we copy them manually
        FlowWithSource full = withDefault.toBuilder()
            .tenantId(tenant)
            .revision(revision)
            .deleted(isDeleted)
            .source(source)
            .build();

        if (templatesEnabled && tenant != null) {
            // This is a hack to set the tenant in template tasks.
            // When using the Template task, we need the tenant to fetch the Template from the database.
            // However, as the task is executed on the Executor we cannot retrieve it from the tenant service and have no other options.
            // So we save it at flow creation/updating time.
            full.allTasksWithChilds().stream().filter(task -> task instanceof Template).forEach(task -> ((Template) task).setTenantId(tenant));
        }

        return full;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> innerInjectDefault(final String tenantId, final String namespace, Map<String, Object> flowAsMap, final boolean onlyVersions) {
        List<PluginDefault> allDefaults = getAllDefaults(tenantId, namespace, flowAsMap);

        if (onlyVersions) {
            // filter only default 'version' property
            allDefaults = allDefaults.stream()
                .map(defaults ->
                {
                    Map<String, Object> filtered = defaults.getValues().entrySet()
                        .stream().filter(entry -> entry.getKey().equals("version"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    return filtered.isEmpty() ? null : defaults.toBuilder().values(filtered).build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        }

        if (allDefaults.isEmpty()) {
            // no defaults to inject - return immediately.
            return flowAsMap;
        }

        // alias-canonicalize all defaults (type-matched and named) so a default declared with a plugin alias
        // also matches the canonical plugin type.
        addAliases(allDefaults);

        // split named (ref) defaults from type-matched defaults: a 'ref' default is applied only to plugins
        // that explicitly opt in via 'pluginDefaultsRef', never by type matching.
        Map<Boolean, List<PluginDefault>> byHasRef = allDefaults
            .stream()
            .collect(Collectors.partitioningBy(pluginDefault -> pluginDefault.getRef() != null));
        List<PluginDefault> typeDefaults = byHasRef.get(false);

        // resolve, per ref, the winning values (shadowing) and the set of types it accepts (incl. alias
        // canonical forms). Shadowing: a forced default always wins over a non-forced one; among forced
        // defaults the lowest-priority (admin) level wins so enforcement cannot be bypassed; among non-forced
        // defaults the highest-priority level wins. (allDefaults is ordered most-important-first: flow, namespace, global.)
        Map<String, RefMatch> refMatches = resolveRefMatches(byHasRef.get(true));

        Map<Boolean, List<PluginDefault>> allDefaultsGroup = typeDefaults
            .stream()
            .collect(Collectors.groupingBy(PluginDefault::isForced, Collectors.toList()));

        // Both maps keep the most-important-first order (flow, namespace, global) of getAllDefaults; the
        // merge direction in defaults() decides which end of the list wins, no reversal needed:
        // non-forced defaults only fill missing keys, so the first (closest) entry wins — flow beats namespace
        // beats global.
        Map<String, List<PluginDefault>> defaults = pluginDefaultsToMap(allDefaultsGroup.getOrDefault(false, Collections.emptyList()));

        // forced defaults stamp over the result, so the last (admin-most) entry wins — global beats namespace beats flow.
        Map<String, List<PluginDefault>> forced = pluginDefaultsToMap(allDefaultsGroup.getOrDefault(true, Collections.emptyList()));

        Object pluginDefaults = flowAsMap.get(PLUGIN_DEFAULTS_FIELD);
        if (pluginDefaults != null) {
            flowAsMap.remove(PLUGIN_DEFAULTS_FIELD);
        }

        // 1. non-forced type-matched defaults — suppressed for plugins that opted into a named (ref) default
        if (!defaults.isEmpty()) {
            flowAsMap = (Map<String, Object>) recursiveDefaults(flowAsMap, defaults, false);
        }

        // 2. named (ref) defaults — applied to plugins declaring a matching 'pluginDefaultsRef', UNLESS a forced
        // type-matched default also applies to that plugin: enforcement takes over entirely and the ref is ignored
        // (it must not stack on top of enforced values). Skipped when only injecting version defaults.
        if (!onlyVersions && !refMatches.isEmpty()) {
            flowAsMap = (Map<String, Object>) recursiveRefDefaults(flowAsMap, refMatches, forced.keySet());
        }

        // 3. forced type-matched defaults are admin enforcement and applied LAST so they win over everything,
        // including a forced ref default — otherwise a flow could bypass enforcement via a forced ref.
        // A WARN is logged when enforced onto a plugin that declared a 'pluginDefaultsRef'.
        if (!forced.isEmpty()) {
            flowAsMap = (Map<String, Object>) recursiveDefaults(flowAsMap, forced, true);
        }

        // 4. consume the 'pluginDefaultsRef' marker for refs that were actually applied (resolved + type match);
        // a surviving marker therefore unambiguously means "unresolved or inapplicable" (validation error + runtime failure).
        if (!onlyVersions && !refMatches.isEmpty()) {
            flowAsMap = (Map<String, Object>) stripAppliedRefMarkers(flowAsMap, refMatches);
        }

        if (pluginDefaults != null) {
            flowAsMap.put(PLUGIN_DEFAULTS_FIELD, pluginDefaults);
        }

        return flowAsMap;

    }

    /**
     * The values applied for a {@code ref} (the shadowing winner) and the set of plugin types it accepts
     * (the declared types of every same-ref default, including alias canonical forms added by {@code addAliases}).
     */
    record RefMatch(PluginDefault winner, Set<String> types) {
    }

    /**
     * Resolves, for each {@code ref}, the winning default and the set of accepted plugin types, given the list of
     * all named defaults ordered most-important-first (flow, namespace, global). A {@code forced} default always
     * wins over a non-forced one. Among forced defaults the lowest-priority (admin) level wins — the last forced
     * occurrence — so an enforced default cannot be bypassed by a higher-level forced override (e.g. a flow-forced
     * ref cannot beat a namespace- or global-forced ref of the same name). Among non-forced defaults the
     * highest-priority level wins — the first occurrence.
     */
    private static Map<String, RefMatch> resolveRefMatches(List<PluginDefault> refDefaults) {
        Map<String, List<PluginDefault>> byRef = refDefaults
            .stream()
            .collect(Collectors.groupingBy(PluginDefault::getRef, LinkedHashMap::new, Collectors.toList()));

        Map<String, RefMatch> matches = new LinkedHashMap<>();
        byRef.forEach((ref, candidates) ->
        {
            List<PluginDefault> forced = candidates.stream().filter(PluginDefault::isForced).toList();
            PluginDefault winner = forced.isEmpty() ? candidates.getFirst() : forced.getLast();
            Set<String> types = candidates.stream()
                .map(PluginDefault::getType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            matches.put(ref, new RefMatch(winner, types));
        });
        return matches;
    }

    /**
     * Validate a plugin default by comparing its properties with the getters of the plugin class.
     * <p>
     * If the plugin default type is unknown,
     * validation will be disabled as we cannot differentiate between a prefix or an unknown type.
     */
    public List<String> validateDefault(PluginDefault pluginDefault) {
        Class<? extends Plugin> classByIdentifier = getClassByIdentifier(pluginDefault);
        if (classByIdentifier == null) {
            // this can either be a prefix or a non-existing plugin, in both cases we cannot validate in detail
            return Collections.emptyList();
        }

        Set<String> pluginDefaultProperties = pluginDefault.getValues().keySet();
        List<String> pluginProperties = Stream.of(classByIdentifier.getMethods())
            .filter(method -> method.getName().startsWith("get") || method.getName().startsWith("is"))
            .map(method ->
            {
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

    protected Class<? extends Plugin> getClassByIdentifier(PluginDefault pluginDefault) {
        return pluginRegistry.findClassByIdentifier(pluginDefault.getType());
    }

    private Map<String, List<PluginDefault>> pluginDefaultsToMap(List<PluginDefault> pluginDefaults) {
        return pluginDefaults
            .stream()
            .collect(Collectors.groupingBy(PluginDefault::getType));
    }

    private void addAliases(List<PluginDefault> allDefaults) {
        List<PluginDefault> aliasedPluginDefault = allDefaults.stream()
            .map(pluginDefault ->
            {
                Class<? extends Plugin> classByIdentifier = getClassByIdentifier(pluginDefault);
                return classByIdentifier != null && !pluginDefault.getType().equals(classByIdentifier.getTypeName()) ? pluginDefault.toBuilder().type(classByIdentifier.getTypeName()).build()
                    : null;
            })
            .filter(Objects::nonNull)
            .toList();

        allDefaults.addAll(aliasedPluginDefault);
    }

    @VisibleForTesting
    Object recursiveDefaults(Object object, Map<String, List<PluginDefault>> defaults) {
        return recursiveDefaults(object, defaults, false);
    }

    Object recursiveDefaults(Object object, Map<String, List<PluginDefault>> defaults, boolean forcedPass) {
        if (object instanceof Map<?, ?> value) {
            value = value
                .entrySet()
                .stream()
                .map(
                    e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        recursiveDefaults(e.getValue(), defaults, forcedPass)
                    )
                )
                .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);

            if (value.containsKey("type")) {
                value = defaults(value, defaults, forcedPass);
            }

            return value;
        } else if (object instanceof Collection<?> value) {
            return value
                .stream()
                .map(r -> recursiveDefaults(r, defaults, forcedPass))
                .toList();
        } else {
            return object;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> defaults(Map<?, ?> plugin, Map<String, List<PluginDefault>> defaults, boolean forcedPass) {
        boolean hasRef = plugin.containsKey(PLUGIN_DEFAULTS_REF_FIELD);

        // a plugin opting into a named (ref) default ('pluginDefaultsRef') ignores non-forced type-matched defaults;
        // forced (admin-enforced) defaults are always applied regardless, see below.
        if (hasRef && !forcedPass) {
            return plugin;
        }

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

        if (hasRef) {
            // forced defaults exist for a plugin that requested a named plugin-defaults: enforcement takes over
            log.warn(
                "A forced pluginDefault for type '{}' is enforced on a plugin declaring pluginDefaultsRef '{}'." +
                    " The referenced plugin-defaults is ignored.",
                pluginType,
                plugin.get(PLUGIN_DEFAULTS_REF_FIELD)
            );
        }

        Map<String, Object> result = (Map<String, Object>) plugin;

        // The merge direction decides who wins (deepMerge: second argument wins), and it differs on purpose.
        // 'matching' is ordered most-important-first (flow, namespace closest-first, global):
        // - non-forced: deepMerge(values, result) — the accumulated result (the plugin's own values plus
        //   already-applied defaults) stays on the winning side, so a default only fills still-missing keys
        //   and the FIRST matching default wins: plugin > flow > namespace > global.
        // - forced: deepMerge(result, values) — the default stamps over the accumulated result, so the LAST
        //   matching default wins, i.e. the admin-most level: global > namespace > flow, so an enforced
        //   higher-level default cannot be bypassed by a forced default at a lower level.
        for (PluginDefault pluginDefault : matching) {
            if (pluginDefault.isForced()) {
                result = MapUtils.deepMerge(result, pluginDefault.getValues());
            } else {
                result = MapUtils.deepMerge(pluginDefault.getValues(), result);
            }
        }

        return result;
    }

    /**
     * Traverses the flow and applies the named ({@code ref}) plugin-defaults to every plugin that opts in via
     * {@code pluginDefaultsRef}. Mirrors {@link #recursiveDefaults(Object, Map)} but matches on the referenced
     * id and the plugin type instead of the plugin type alone. The {@code pluginDefaultsRef} marker is consumed
     * afterwards by {@link #stripAppliedRefMarkers(Object, Map)}.
     */
    @VisibleForTesting
    Object recursiveRefDefaults(Object object, Map<String, RefMatch> refMatches, Set<String> forcedTypes) {
        if (object instanceof Map<?, ?> value) {
            value = value
                .entrySet()
                .stream()
                .map(
                    e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        recursiveRefDefaults(e.getValue(), refMatches, forcedTypes)
                    )
                )
                .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);

            if (value.containsKey(PLUGIN_DEFAULTS_REF_FIELD)) {
                value = refDefaults(value, refMatches, forcedTypes);
            }

            return value;
        } else if (object instanceof Collection<?> value) {
            return value
                .stream()
                .map(r -> recursiveRefDefaults(r, refMatches, forcedTypes))
                .toList();
        } else {
            return object;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> refDefaults(Map<?, ?> plugin, Map<String, RefMatch> refMatches, Set<String> forcedTypes) {
        Object ref = plugin.get(PLUGIN_DEFAULTS_REF_FIELD);
        if (!(ref instanceof String refId)) {
            return plugin;
        }

        RefMatch match = refMatches.get(refId);
        if (match == null) {
            // unknown ref: keep 'pluginDefaultsRef' so a surviving marker signals an unresolved reference
            // (flagged as a validation error in the editor and failed at runtime).
            log.warn("No plugin-defaults exists for pluginDefaultsRef '{}' referenced by plugin of type '{}'", refId, plugin.get("type"));
            return plugin;
        }

        if (!typeMatches(plugin.get("type"), match.types())) {
            // the named plugin-defaults is scoped to a different plugin type: do not apply it, and keep the
            // marker so it surfaces the same way as an unresolved reference (validation error + runtime failure).
            log.warn(
                "The plugin-defaults for pluginDefaultsRef '{}' is declared for type(s) '{}' but is referenced by a plugin of type '{}'; it will not be applied",
                refId, match.types(), plugin.get("type")
            );
            return plugin;
        }

        if (typeMatches(plugin.get("type"), forcedTypes)) {
            // a forced (admin-enforced) type default also matches this plugin: enforcement takes over entirely
            // and the referenced plugin-defaults is ignored (it must not stack on top of enforced values).
            // The marker is still consumed by stripAppliedRefMarkers since the reference was valid.
            return plugin;
        }

        PluginDefault winner = match.winner();
        if (winner.isForced()) {
            // forced plugin-defaults overrides the plugin's own values
            return MapUtils.deepMerge((Map<String, Object>) plugin, winner.getValues());
        }
        // non-forced plugin-defaults yields to the plugin's explicit values
        return MapUtils.deepMerge(winner.getValues(), (Map<String, Object>) plugin);
    }

    /**
     * Whether the plugin-defaults referenced by a plugin both exists and is scoped to the plugin's type.
     */
    private static boolean refApplies(Map<?, ?> plugin, Map<String, RefMatch> refMatches) {
        if (!(plugin.get(PLUGIN_DEFAULTS_REF_FIELD) instanceof String refId)) {
            return false;
        }
        RefMatch match = refMatches.get(refId);
        return match != null && typeMatches(plugin.get("type"), match.types());
    }

    private static boolean typeMatches(Object pluginType, Set<String> refTypes) {
        return pluginType instanceof String type
            && refTypes.stream().anyMatch(refType -> type.equals(refType) || type.startsWith(refType));
    }

    /**
     * Removes the {@code pluginDefaultsRef} marker from every plugin whose reference was actually applied
     * (i.e. it resolved and was type-compatible). A marker left in place means the reference was unresolved
     * or inapplicable, which is surfaced as a validation error and fails the plugin at runtime.
     */
    @VisibleForTesting
    Object stripAppliedRefMarkers(Object object, Map<String, RefMatch> refMatches) {
        if (object instanceof Map<?, ?> value) {
            Map<Object, Object> result = value
                .entrySet()
                .stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), stripAppliedRefMarkers(e.getValue(), refMatches)))
                .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);

            if (refApplies(result, refMatches)) {
                result.remove(PLUGIN_DEFAULTS_REF_FIELD);
            }

            return result;
        } else if (object instanceof Collection<?> value) {
            return value
                .stream()
                .map(r -> stripAppliedRefMarkers(r, refMatches))
                .toList();
        } else {
            return object;
        }
    }

    /**
     * Returns the distinct {@code pluginDefaultsRef} ids that remain unresolved (or inapplicable) in an
     * already-defaulted flow. A reference is unresolved when no plugin-defaults with that {@code ref} exists
     * at flow, namespace or global level, or when the one that exists is scoped to a different plugin type
     * (the marker is otherwise stripped during {@link #stripAppliedRefMarkers(Object, Map)}).
     */
    public Set<String> unresolvedPluginDefaultsRefs(FlowInterface flowWithDefaults) {
        Map<String, Object> flowAsMap = OBJECT_MAPPER.convertValue(flowWithDefaults, JacksonMapper.MAP_TYPE_REFERENCE);
        Set<String> refs = new LinkedHashSet<>();
        collectUnresolvedRefs(flowAsMap, refs);
        return refs;
    }

    private void collectUnresolvedRefs(Object object, Set<String> refs) {
        if (object instanceof Map<?, ?> value) {
            if (value.get(PLUGIN_DEFAULTS_REF_FIELD) instanceof String ref) {
                refs.add(ref);
            }
            value.values().forEach(v -> collectUnresolvedRefs(v, refs));
        } else if (object instanceof Collection<?> value) {
            value.forEach(v -> collectUnresolvedRefs(v, refs));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DEPRECATED
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * @deprecated use {@link #injectAllDefaults(FlowInterface, Logger)} instead
     */
    @Deprecated(forRemoval = true, since = "0.20")
    public Flow injectDefaults(Flow flow, Logger logger) {
        try {
            return this.injectDefaults(flow);
        } catch (Exception e) {
            logger.warn(
                "Can't inject plugin defaults on tenant {}, namespace '{}', flow '{}' with errors '{}'",
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getId(),
                e.getMessage(),
                e
            );
            return flow;
        }
    }

    /**
     * @deprecated use {@link #injectAllDefaults(FlowInterface, boolean)} instead
     */
    @Deprecated(forRemoval = true, since = "0.20")
    public Flow injectDefaults(Flow flow) throws ConstraintViolationException {
        if (flow instanceof FlowWithSource flowWithSource) {
            try {
                return this.injectAllDefaults(flowWithSource, false);
            } catch (FlowProcessingException e) {
                if (e.getCause() instanceof ConstraintViolationException cve) {
                    throw cve;
                }
                throw new KestraRuntimeException(e);
            }
        }

        Map<String, Object> mapFlow = NON_DEFAULT_OBJECT_MAPPER.convertValue(flow, JacksonMapper.MAP_TYPE_REFERENCE);
        mapFlow = innerInjectDefault(flow.getTenantId(), flow.getNamespace(), mapFlow, false);
        return YamlParser.parse(mapFlow, Flow.class, false);
    }
}
