package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.ListUtils;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Provides business logic to manipulate {@link Flow}
 */
@Singleton
@Slf4j
public class FlowService {
    private static final ObjectMapper NON_DEFAULT_OBJECT_MAPPER = JacksonMapper.ofJson()
        .copy()
        .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

    @Inject
    Optional<FlowRepositoryInterface> flowRepository;

    @Inject
    YamlFlowParser yamlFlowParser;

    @Inject
    PluginDefaultService pluginDefaultService;

    @Inject
    PluginRegistry pluginRegistry;

    public FlowWithSource importFlow(String tenantId, String source) {
        return this.importFlow(tenantId, source, false);
    }

    public FlowWithSource importFlow(String tenantId, String source, boolean dryRun) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        Flow withTenant = yamlFlowParser.parse(source, Flow.class).toBuilder()
            .tenantId(tenantId)
            .build();

        FlowRepositoryInterface flowRepository = this.flowRepository.get();
        Optional<FlowWithSource> flowWithSource = flowRepository
            .findByIdWithSource(withTenant.getTenantId(), withTenant.getNamespace(), withTenant.getId(), Optional.empty(), true);
        if (dryRun) {
            return flowWithSource
                .map(previous -> {
                    if (previous.equals(withTenant, source) && !previous.isDeleted()) {
                        return previous;
                    } else {
                        return FlowWithSource.of(withTenant.toBuilder().revision(previous.getRevision() + 1).build(), source);
                    }
                })
                .orElseGet(() -> FlowWithSource.of(withTenant, source).toBuilder().revision(1).build());
        }

        return flowWithSource
            .map(previous -> flowRepository.update(withTenant, previous, source, pluginDefaultService.injectDefaults(withTenant)))
            .orElseGet(() -> flowRepository.create(withTenant, source, pluginDefaultService.injectDefaults(withTenant)));
    }

    public List<FlowWithSource> findByNamespaceWithSource(String tenantId, String namespace) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return flowRepository.get().findByNamespaceWithSource(tenantId, namespace);
    }

    public List<Flow> findAll(String tenantId) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return flowRepository.get().findAll(tenantId);
    }

    public List<Flow> findByNamespace(String tenantId, String namespace) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return flowRepository.get().findByNamespace(tenantId, namespace);
    }

    public List<Flow> findByNamespacePrefix(String tenantId, String namespacePrefix) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return flowRepository.get().findByNamespacePrefix(tenantId, namespacePrefix);
    }

    public Flow delete(Flow flow) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return flowRepository.get().delete(flow);
    }

    public Stream<Flow> keepLastVersion(Stream<Flow> stream) {
        return keepLastVersionCollector(stream);
    }

    public List<String> deprecationPaths(Flow flow) {
        return deprecationTraversal("", flow).toList();
    }

    public List<String> warnings(Flow flow) {
        if (flow == null) {
            return Collections.emptyList();
        }

        List<String> warnings = new ArrayList<>();
        List<AbstractTrigger> triggers = flow.getTriggers();
        if (
            triggers != null &&
                triggers.stream().anyMatch(trigger -> {
                    if (trigger instanceof io.kestra.plugin.core.trigger.Flow flowTrigger) {
                        return Optional.ofNullable(flowTrigger.getConditions()).map(List::isEmpty).orElse(true);
                    }

                    return false;
                    })
        ) {
            warnings.add("This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the conditions property to the Flow trigger.");
        }

        return warnings;
    }

    public List<Relocation> relocations(String flowSource) {
        try {
            Map<String, Class<?>> aliases = pluginRegistry.plugins().stream()
                .flatMap(plugin -> plugin.getAliases().values().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Map<String, Object> stringObjectMap = JacksonMapper.ofYaml().readValue(flowSource, JacksonMapper.MAP_TYPE_REFERENCE);
            return relocations(aliases, stringObjectMap);
        } catch (JsonProcessingException e) {
            // silent failure (we don't compromise the app / response for warnings)
            return Collections.emptyList();
        }
    }
    public record Relocation(String from, String to) {}

    @SuppressWarnings("unchecked")
    private List<Relocation> relocations(Map<String, Class<?>> aliases, Map<String, Object> stringObjectMap) {
        List<Relocation> relocations = new ArrayList<>();
        for (Map.Entry<String, Object> entry : stringObjectMap.entrySet()) {
            if (entry.getValue() instanceof String value && aliases.containsKey(value)) {
                relocations.add(new Relocation(value, aliases.get(value).getName()));
            }

            if (entry.getValue() instanceof Map<?, ?> value) {
                relocations.addAll(relocations(aliases, (Map<String, Object>) value));
            }

            if (entry.getValue() instanceof List<?> value) {
                List<Relocation> listAliases = value.stream().flatMap(item -> {
                    if (item instanceof Map<?, ?> map) {
                        return relocations(aliases, (Map<String, Object>) map).stream();
                    }
                    return Stream.empty();
                }).toList();
                relocations.addAll(listAliases);
            }
        }

        return relocations;
    }


    private Stream<String> deprecationTraversal(String prefix, Object object) {
        if (object == null || ClassUtils.isPrimitiveOrWrapper(object.getClass()) || String.class.equals(object.getClass())) {
            return Stream.empty();
        }

        return Stream.concat(
            object.getClass().isAnnotationPresent(Deprecated.class) ? Stream.of(prefix) : Stream.empty(),
            allGetters(object.getClass())
                .flatMap(method -> {
                    try {
                        Object fieldValue = method.invoke(object);

                        if (fieldValue instanceof Iterable<?> iterableValue) {
                            fieldValue = StreamSupport.stream(iterableValue.spliterator(), false).toArray(Object[]::new);
                        }

                        String fieldName = method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4);
                        Stream<String> additionalDeprecationPaths = Stream.empty();
                        if (fieldValue instanceof Object[] arrayValue) {
                            additionalDeprecationPaths = IntStream.range(0, arrayValue.length).boxed().flatMap(i -> deprecationTraversal(fieldName + "[%d]".formatted(i), arrayValue[i]));
                        }

                        return Stream.concat(
                            method.isAnnotationPresent(Deprecated.class) && fieldValue != null ? Stream.of(prefix.isEmpty() ? fieldName : prefix + "." + fieldName) : Stream.empty(),
                            additionalDeprecationPaths
                        );
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        // silent failure (we don't compromise the app / response for warnings)
                    }

                    return Stream.empty();
                })
        );
    }

    private Stream<Method> allGetters(Class<?> clazz) {
        return Arrays.stream(clazz.getMethods())
            .filter(m -> !m.getDeclaringClass().equals(Object.class))
            .filter(method -> method.getName().startsWith("get") && method.getName().length() > 3 && method.getParameterCount() == 0)
            .filter(method -> !method.getReturnType().equals(Void.TYPE))
            .filter(method -> !Modifier.isStatic(method.getModifiers()));
    }

    public Flow keepLastVersion(Stream<Flow> stream, String namespace, String flowId) {
        return keepLastVersionCollector(
            stream
                .filter(flow -> flow.getNamespace().equals(namespace) && flow.getId().equals(flowId))
        )
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "." + flowId + "'"));
    }

    public Collection<Flow> keepLastVersion(List<Flow> flows) {
        return keepLastVersionCollector(flows.stream())
            .toList();
    }

    private Stream<Flow> keepLastVersionCollector(Stream<Flow> stream) {
        return stream
            .sorted((left, right) -> left.getRevision() > right.getRevision() ? -1 : (left.getRevision().equals(right.getRevision()) ? 0 : 1))
            .collect(Collectors.groupingBy(Flow::uidWithoutRevision))
            .values()
            .stream()
            .map(flows -> {
                Flow flow = flows.stream().findFirst().orElseThrow();

                // edge case, 2 flows with same revision, we keep the deleted
                final Flow finalFlow = flow;
                Optional<Flow> deleted = flows.stream()
                    .filter(f -> f.getRevision().equals(finalFlow.getRevision()) && f.isDeleted())
                    .findFirst();

                if (deleted.isPresent()) {
                    return null;
                }

                return flow.isDeleted() ? null : flow;
            })
            .filter(Objects::nonNull);
    }

    protected boolean removeUnwanted(Flow f, Execution execution) {
        // we don't allow recursive
        return !f.uidWithoutRevision().equals(Flow.uidWithoutRevision(execution));
    }

    public static List<AbstractTrigger> findRemovedTrigger(Flow flow, Flow previous) {
        return ListUtils.emptyOnNull(previous.getTriggers())
            .stream()
            .filter(p -> ListUtils.emptyOnNull(flow.getTriggers())
                .stream()
                .noneMatch(c -> c.getId().equals(p.getId()))
            )
            .toList();
    }

    public static List<AbstractTrigger> findUpdatedTrigger(Flow flow, Flow previous) {
        return ListUtils.emptyOnNull(flow.getTriggers())
            .stream()
            .filter(oldTrigger -> ListUtils.emptyOnNull(previous.getTriggers())
                .stream()
                .anyMatch(trigger -> trigger.getId().equals(oldTrigger.getId()) && !trigger.equals(oldTrigger))
            )
            .toList();
    }

    public static String cleanupSource(String source) {
        return source.replaceFirst("(?m)^revision: \\d+\n?", "");
    }

    public static String injectDisabled(String source, Boolean disabled) {
        String regex = disabled ? "^disabled\\s*:\\s*false\\s*" : "^disabled\\s*:\\s*true\\s*";

        Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
        if (p.matcher(source).find()) {
            return p.matcher(source).replaceAll(String.format("disabled: %s\n", disabled));
        }

        return source + String.format("\ndisabled: %s", disabled);
    }

    public static String generateSource(Flow flow, @Nullable String source) {
        try {
            if (source == null) {
                return toYamlWithoutDefault(flow);
            }

            if (JacksonMapper.ofYaml().writeValueAsString(flow).equals(source)) {
                source = toYamlWithoutDefault(flow);
            }
        } catch (JsonProcessingException e) {
            log.warn("Unable to convert flow json '{}' '{}'({})", flow.getNamespace(), flow.getId(), flow.getRevision(), e);
        }

        return source;
    }

    @SneakyThrows
    private static String toYamlWithoutDefault(Object object) throws JsonProcessingException {
        String json = NON_DEFAULT_OBJECT_MAPPER.writeValueAsString(object);

        Object map = fixSnakeYaml(JacksonMapper.toMap(json));

        return JacksonMapper.ofYaml().writeValueAsString(map);
    }

    /**
     * Dirty hack but only concern previous flow with no source code in org.yaml.snakeyaml.emitter.Emitter:
     * <pre>
     * if (previousSpace) {
     *   spaceBreak = true;
     * }
     * </pre>
     * This control will detect ` \n` as a no valid entry on a string and will break the multiline to transform in single line
     *
     * @param object the object to fix
     * @return the modified object
     */
    private static Object fixSnakeYaml(Object object) {
        if (object instanceof Map) {
            return ((Map<?, ?>) object)
                .entrySet()
                .stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(
                    fixSnakeYaml(entry.getKey()),
                    fixSnakeYaml(entry.getValue())
                ))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (u, v) -> {
                        throw new IllegalStateException(String.format("Duplicate key %s", u));
                    },
                    LinkedHashMap::new
                ));
        } else if (object instanceof Collection) {
            return ((Collection<?>) object)
                .stream()
                .map(FlowService::fixSnakeYaml)
                .toList();
        } else if (object instanceof String) {
            String item = (String) object;

            if (item.contains("\n")) {
                return item.replaceAll("\\s+\\n", "\\\n");
            }
        }

        return object;
    }

    /**
     * Return true if the namespace is allowed from the namespace denoted by 'fromTenant' and 'fromNamespace'.
     * As namespace restriction is an EE feature, this will always return true in OSS.
     */
    public boolean isAllowedNamespace(String tenant, String namespace, String fromTenant, String fromNamespace) {
        return true;
    }

    /**
     * Check that the namespace is allowed from the namespace denoted by 'fromTenant' and 'fromNamespace'.
     * If not, throw an IllegalArgumentException.
     */
    public void checkAllowedNamespace(String tenant, String namespace, String fromTenant, String fromNamespace) {
        if (!isAllowedNamespace(tenant, namespace, fromTenant, fromNamespace)) {
            throw new IllegalArgumentException("Namespace " + namespace + " is not allowed.");
        }
    }

    /**
     * Return true if the namespace is allowed from all the namespace in the 'fromTenant' tenant.
     * As namespace restriction is an EE feature, this will always return true in OSS.
     */
    public boolean areAllowedAllNamespaces(String tenant, String fromTenant, String fromNamespace) {
        return true;
    }

    /**
     * Check that the namespace is allowed from all the namespace in the 'fromTenant' tenant.
     * If not, throw an IllegalArgumentException.
     */
    public void checkAllowedAllNamespaces(String tenant, String fromTenant, String fromNamespace) {
        if (!areAllowedAllNamespaces(tenant, fromTenant, fromNamespace)) {
            throw new IllegalArgumentException("All namespaces are not allowed, you should either filter on a namespace or configure all namespaces to allow your namespace.");
        }
    }

    /**
     * Gets the executable flow for the given namespace, id, and revision.
     * Warning: this method bypasses ACL so someone with only execution right can create a flow execution
     *
     * @param tenant    Rhe tenant ID.
     * @param namespace The flow's namespace.
     * @param id        The flow's ID.
     * @param revision  The flow's revision.
     * @return The {@link Flow}.
     * @throws NoSuchElementException if the requested flow does not exist.
     * @throws IllegalStateException  if the requested flow is not executable.
     */
    public Flow getFlowIfExecutableOrThrow(final String tenant, final String namespace, final String id, final Optional<Integer> revision) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        Optional<Flow> optional = flowRepository.get().findByIdWithoutAcl(tenant, namespace, id, revision);
        if (optional.isEmpty()) {
            throw new NoSuchElementException("Requested Flow is not found.");
        }

        Flow flow = optional.get();
        if (flow.isDisabled()) {
            throw new IllegalStateException("Requested Flow is disabled.");
        }

        if (flow instanceof FlowWithException fwe ) {
            throw new IllegalStateException("Requested Flow is not valid. Error: " + fwe.getException());
        }
        return flow;
    }

    private IllegalStateException noRepositoryException() {
        return new IllegalStateException("No flow repository found. Make sure the `kestra.repository.type` property is set.");
    }

    /**
     * Validates that a given flow is valid and could be executed.
     *
     * @param tenant  The tenant ID.
     * @param flow    The flow to validate.
     *
     * @throws InvalidException if the given flow is not valid.
     */
    public void verifyFlowIsValidAndExecutable(final String tenant, final Flow flow) throws InvalidException {
        // noop - by default, we always expect a flow to be valid.
    }
}
