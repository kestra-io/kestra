package io.kestra.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.ListUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
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
    YamlParser yamlParser;

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

        FlowWithSource withTenant = yamlParser.parse(source, Flow.class).toBuilder()
            .tenantId(tenantId)
            .build()
            .withSource(source);

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

    public Optional<Flow> findById(String tenantId, String namespace, String flowId) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return flowRepository.get().findById(tenantId, namespace, flowId);
    }

    public Stream<FlowWithSource> keepLastVersion(Stream<FlowWithSource> stream) {
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
        List<io.kestra.plugin.core.trigger.Flow> flowTriggers = ListUtils.emptyOnNull(flow.getTriggers()).stream()
            .filter(io.kestra.plugin.core.trigger.Flow.class::isInstance)
            .map(io.kestra.plugin.core.trigger.Flow.class::cast)
            .toList();
        flowTriggers.forEach(flowTrigger -> {
            if (ListUtils.emptyOnNull(flowTrigger.getConditions()).isEmpty() && flowTrigger.getPreconditions() == null) {
                warnings.add("This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the preconditions property to the Flow trigger '" + flowTrigger.getId() + "'.");
            }
        });

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

    // check if subflow is present in given namespace
    public void checkValidSubflows(Flow flow) {
        List<io.kestra.plugin.core.flow.Subflow> subFlows = ListUtils.emptyOnNull(flow.getTasks()).stream()
            .filter(io.kestra.plugin.core.flow.Subflow.class::isInstance)
            .map(io.kestra.plugin.core.flow.Subflow.class::cast)
            .toList();

        Set<ConstraintViolation<?>> violations = new HashSet<>();

        subFlows.forEach(subflow -> {
            Optional<Flow> optional = findById(flow.getTenantId(), subflow.getNamespace(), subflow.getFlowId());

            if (optional.isEmpty()) {
                violations.add(ManualConstraintViolation.of(
                    "The subflow '" + subflow.getFlowId() + "' not found in namespace '" + subflow.getNamespace() + "'.",
                    flow,
                    Flow.class,
                    "flow.tasks",
                    flow.getNamespace()
                ));
            }
        });

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
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

    public Collection<FlowWithSource> keepLastVersion(List<FlowWithSource> flows) {
        return keepLastVersionCollector(flows.stream()).toList();
    }

    public Stream<FlowWithSource> keepLastVersionCollector(Stream<FlowWithSource> stream) {
        // Use a Map to track the latest version of each flow
        Map<String, FlowWithSource> latestFlows = new HashMap<>();

        stream.forEach(flow -> {
            String uid = flow.uidWithoutRevision();
            FlowWithSource existing = latestFlows.get(uid);

            // Update only if the current flow has a higher revision
            if (existing == null || flow.getRevision() > existing.getRevision()) {
                latestFlows.put(uid, flow);
            } else if (flow.getRevision().equals(existing.getRevision()) && flow.isDeleted()) {
                // Edge case: prefer deleted flow with the same revision
                latestFlows.put(uid, flow);
            }
        });

        // Return the non-deleted flows
        return latestFlows.values().stream().filter(flow -> !flow.isDeleted());
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
                .anyMatch(trigger -> trigger.getId().equals(oldTrigger.getId()) && !EqualsBuilder.reflectionEquals(trigger, oldTrigger))
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

    public static String generateSource(Flow flow) {
        try {
            String json = NON_DEFAULT_OBJECT_MAPPER.writeValueAsString(flow);

            Object map = fixSnakeYaml(JacksonMapper.toMap(json));

            String source = JacksonMapper.ofYaml().writeValueAsString(map);

            // remove the revision from the generated source
            return source.replaceFirst("(?m)^revision: \\d+\n?","");
        } catch (JsonProcessingException e) {
            log.warn("Unable to convert flow json '{}' '{}'({})", flow.getNamespace(), flow.getId(), flow.getRevision(), e);
            return null;
        }
    }

    // Used in Git plugin
    public List<Flow> findByNamespacePrefix(String tenantId, String namespacePrefix) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return flowRepository.get().findByNamespacePrefix(tenantId, namespacePrefix);
    }

    // Used in Git plugin
    public FlowWithSource delete(FlowWithSource flow) {
        if (flowRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return flowRepository.get().delete(flow);
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
        if (object instanceof Map<?, ?> mapValue) {
            return mapValue
                .entrySet()
                .stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(
                    fixSnakeYaml(entry.getKey()),
                    fixSnakeYaml(entry.getValue())
                ))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (u, v) -> {
                        throw new IllegalStateException(String.format("Duplicate key %s", u));
                    },
                    LinkedHashMap::new
                ));
        } else if (object instanceof Collection<?> collectionValue) {
            return collectionValue
                .stream()
                .map(FlowService::fixSnakeYaml)
                .toList();
        } else if (object instanceof String item) {
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
}
