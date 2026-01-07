package io.kestra.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.Pause;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Provides business logic for manipulating flow objects.
 */
@Singleton
@Slf4j
public class FlowService {
    @Inject
    Optional<FlowRepositoryInterface> flowRepository;

    @Inject
    PluginDefaultService pluginDefaultService;

    @Inject
    PluginRegistry pluginRegistry;

    @Inject
    ModelValidator modelValidator;

    @Inject
    Optional<FlowTopologyRepositoryInterface> flowTopologyRepository;

    @Inject
    Provider<RunContextFactory> runContextFactory; // Lazy init: avoid circular dependency error.

    /**
     * Validates and creates the given flow.
     * <p>
     * The validation of the flow is done from the source after injecting all plugin default values.
     *
     * @param flow             The flow.
     * @param strictValidation Specifies whether to perform a strict validation of the flow.
     * @return The created {@link FlowWithSource}.
     */
    public FlowWithSource create(GenericFlow flow, final boolean strictValidation) throws FlowProcessingException {
        Objects.requireNonNull(flow, "Cannot create null flow");
        if (flow.getSource() == null || flow.getSource().isBlank()) {
            throw new IllegalArgumentException("Cannot create flow with null or blank source");
        }

        // Inject plugin default versions, and perform parsing validation when strictValidation = true (i.e., checking unknown and duplicated properties).
        FlowWithSource parsed = pluginDefaultService.parseFlowWithVersionDefaults(flow.getTenantId(), flow.getSource(), strictValidation);

        // Validate Flow with defaults values
        // Do not perform a strict parsing validation to ignore unknown
        // properties that might be injecting through default values.
        modelValidator.validate(pluginDefaultService.injectAllDefaults(parsed, false));

        return repository().create(flow);
    }

    private FlowRepositoryInterface repository() {
        return flowRepository
            .orElseThrow(() -> new IllegalStateException("Cannot perform operation on flow. Cause: No FlowRepository"));
    }
    private static String formatValidationError(String message) {
        if (message.startsWith("Illegal flow source:")) {
            // Already formatted by YamlParser, return as-is
            return message;
        }
        // For other validation errors, provide context
        return "Validation error: " + message;
    }
    /**
     * Evaluates all checks defined in the given flow using the provided inputs.
     * <p>
     * Each check's {@link Check#getCondition()} is evaluated in the context of the flow.
     * If a condition evaluates to {@code false} or fails to evaluate due to a
     * variable error, the corresponding {@link Check} is added to the returned list.
     * </p>
     *
     * @param flow   the flow containing the checks to evaluate
     * @param inputs the input values used when evaluating the conditions
     * @return a list of checks whose conditions evaluated to {@code false} or failed to evaluate
     */
    public List<Check> getFailedChecks(Flow flow, Map<String, Object> inputs) {
        if (!ListUtils.isEmpty(flow.getChecks())) {
            RunContext runContext = runContextFactory.get().of(flow, Map.of("inputs", inputs));
            List<Check> falseConditions = new ArrayList<>();
            for (Check check : flow.getChecks()) {
                try {
                    boolean result = Boolean.TRUE.equals(runContext.renderTyped(check.getCondition()));
                    if (!result) {
                        falseConditions.add(check);
                    }
                } catch (IllegalVariableEvaluationException e) {
                    log.debug("[tenant: {}] [namespace: {}] [flow: {}] Failed to evaluate check condition. Cause.: {}",
                        flow.getTenantId(),
                        flow.getNamespace(),
                        flow.getId(),
                        e.getMessage(),
                        e
                    );
                    falseConditions.add(Check
                        .builder()
                            .message("Failed to evaluate check condition. Cause: " + e.getMessage())
                            .behavior(Check.Behavior.BLOCK_EXECUTION)
                            .style(Check.Style.ERROR)
                        .build()
                    );
                }
            }
            return falseConditions;
        }
        return List.of();
    }

    /**
     * Validates the given flow source.
     * <p>
     * the YAML source can contain one or many objects.
     *
     * @param tenantId  The tenant identifier.
     * @param flows     The YAML source.
     * @return  The list validation constraint violations.
     */
    public List<ValidateConstraintViolation> validate(final String tenantId, final String flows) {
        AtomicInteger index = new AtomicInteger(0);
        return Stream
            .of(flows.split("\\n+---\\n*?"))
            .map(source -> {
                ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();
                validateConstraintViolationBuilder.index(index.getAndIncrement());

                try {
                    FlowWithSource flow = pluginDefaultService.parseFlowWithVersionDefaults(tenantId, source, true);
                    Integer sentRevision = flow.getRevision();
                    if (sentRevision != null) {
                        Integer lastRevision = Optional.ofNullable(repository().lastRevision(tenantId, flow.getNamespace(), flow.getId()))
                            .orElse(0);
                        validateConstraintViolationBuilder.outdated(!sentRevision.equals(lastRevision + 1));
                    }

                    validateConstraintViolationBuilder.deprecationPaths(deprecationPaths(flow));
                    validateConstraintViolationBuilder.warnings(warnings(flow, tenantId));
                    validateConstraintViolationBuilder.infos(relocations(source).stream().map(relocation -> relocation.from() + " is replaced by " + relocation.to()).toList());
                    validateConstraintViolationBuilder.flow(flow.getId());
                    validateConstraintViolationBuilder.namespace(flow.getNamespace());

                    // Do not perform a strict parsing validation to ignore unknown
                    // properties that might be injecting through default values.
                    modelValidator.validate(pluginDefaultService.injectAllDefaults(flow, false));

                } catch (ConstraintViolationException e) {
                    String friendlyMessage = formatValidationError(e.getMessage());
                    validateConstraintViolationBuilder.constraints(friendlyMessage);
                } catch (FlowProcessingException e) {
                    if (e.getCause() instanceof ConstraintViolationException cve) {
                        String friendlyMessage = formatValidationError(cve.getMessage());
                        validateConstraintViolationBuilder.constraints(friendlyMessage);
                    } else {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        validateConstraintViolationBuilder.constraints("Unable to validate the flow: " + cause.getMessage());
                    }
                } catch (RuntimeException re) {
                    // In case of any error, we add a validation violation so the error is displayed in the UI.
                    // We may change that by throwing an internal error and handle it in the UI, but this should not occur except for rare cases
                    // in dev like incompatible plugin versions.
                    log.error("Unable to validate the flow", re);
                    validateConstraintViolationBuilder.constraints("Unable to validate the flow: " + re.getMessage());
                }
                return validateConstraintViolationBuilder.build();
            })
            .collect(Collectors.toList());
    }

    public FlowWithSource importFlow(String tenantId, String source) throws FlowProcessingException {
        return this.importFlow(tenantId, source, false);
    }

    public FlowWithSource importFlow(String tenantId, String source, boolean dryRun) throws FlowProcessingException {

        final GenericFlow flow = GenericFlow.fromYaml(tenantId, source);

        Optional<FlowWithSource> maybeExisting = repository().findByIdWithSource(
            flow.getTenantId(),
            flow.getNamespace(),
            flow.getId(),
            Optional.empty(),
            true
        );

        // Inject default plugin 'version' props before converting
        // to flow to correctly resolve all plugin type.
        FlowWithSource flowToImport = pluginDefaultService.injectVersionDefaults(flow, false);

        if (dryRun) {
            return maybeExisting
                .map(previous -> previous.isSameWithSource(flowToImport) && !previous.isDeleted() ?
                    previous :
                    FlowWithSource.of(flowToImport.toBuilder().revision(previous.getRevision() + 1).build(), source)
                )
                .orElseGet(() -> FlowWithSource.of(flowToImport, source).toBuilder().tenantId(tenantId).revision(1).build());
        } else {
            return maybeExisting
                .map(previous -> repository().update(flow, previous))
                .orElseGet(() -> repository().create(flow));
        }
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

    public Stream<FlowInterface> keepLastVersion(Stream<FlowInterface> stream) {
        return keepLastVersionCollector(stream);
    }

    public List<String> deprecationPaths(Flow flow) {
        return deprecationTraversal("", flow).toList();
    }


    public List<String> warnings(Flow flow, String tenantId) {
        if (flow == null) {
            return Collections.emptyList();
        }

        List<String> warnings = new ArrayList<>(checkValidSubflows(flow, tenantId));

        List<io.kestra.plugin.core.trigger.Flow> flowTriggers = ListUtils.emptyOnNull(flow.getTriggers()).stream()
            .filter(io.kestra.plugin.core.trigger.Flow.class::isInstance)
            .map(io.kestra.plugin.core.trigger.Flow.class::cast)
            .toList();
        flowTriggers.forEach(flowTrigger -> {
            if (ListUtils.emptyOnNull(flowTrigger.getConditions()).isEmpty() && flowTrigger.getPreconditions() == null) {
                warnings.add("This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the preconditions property to the Flow trigger '" + flowTrigger.getId() + "'.");
            }
        });

        // add warning for runnable properties (timeout, workerGroup, taskCache) when used not in a runnable
        flow.allTasksWithChilds().forEach(task -> {
            if (!(task instanceof RunnableTask<?>)) {
                if (task.getTimeout() != null && !(task instanceof Pause)) {
                    warnings.add("The task '" + task.getId() + "' cannot use the 'timeout' property as it's only relevant for runnable tasks.");
                }
                if (task.getTaskCache() != null) {
                    warnings.add("The task '" + task.getId() + "' cannot use the 'taskCache' property as it's only relevant for runnable tasks.");
                }
                if (task.getWorkerGroup() != null) {
                    warnings.add("The task '" + task.getId() + "' cannot use the 'workerGroup' property as it's only relevant for runnable tasks.");
                }
            }
        });

        return warnings;
    }

    public List<Relocation> relocations(String flowSource) {
        try {
            Map<String, Class<?>> aliases = pluginRegistry.plugins().stream()
                .flatMap(plugin -> plugin.getAliases().values().stream())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (existing, duplicate) -> existing
                ));
            Map<String, Object> stringObjectMap = JacksonMapper.ofYaml().readValue(flowSource, JacksonMapper.MAP_TYPE_REFERENCE);
            return relocations(aliases, stringObjectMap);
        } catch (JsonProcessingException e) {
            // silent failure (we don't compromise the app / response for warnings)
            return Collections.emptyList();
        }
    }

    // check if subflow is present in given namespace
    public List<String> checkValidSubflows(Flow flow, String tenantId) {
        List<io.kestra.plugin.core.flow.Subflow> subFlows = ListUtils.emptyOnNull(flow.getTasks()).stream()
            .filter(io.kestra.plugin.core.flow.Subflow.class::isInstance)
            .map(io.kestra.plugin.core.flow.Subflow.class::cast)
            .toList();

        List<String> violations = new ArrayList<>();

        subFlows.forEach(subflow -> {
            String regex = ".*\\{\\{.+}}.*"; // regex to check if string contains pebble
            String subflowId = subflow.getFlowId();
            String namespace = subflow.getNamespace();
            if ((subflowId != null && subflowId.matches(regex)) || (namespace != null && namespace.matches(regex))) {
                return;
            }
            if (subflowId == null || namespace == null) {
                // those fields are mandatory so the mandatory validation will apply
                return;
            }
            Optional<Flow> optional = findById(tenantId, subflow.getNamespace(), subflow.getFlowId());

            if (optional.isEmpty()) {
                violations.add("The subflow '" + subflow.getFlowId() + "' not found in namespace '" + subflow.getNamespace() + "'.");
            } else if (optional.get().isDisabled()) {
                violations.add("The subflow '" + subflow.getFlowId() + "' is disabled in namespace '" + subflow.getNamespace() + "'.");
            }
        });

        return violations;
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

    public Collection<FlowInterface> keepLastVersion(List<FlowInterface> flows) {
        return keepLastVersionCollector(flows.stream()).toList();
    }

    public Stream<FlowInterface> keepLastVersionCollector(Stream<FlowInterface> stream) {
        // Use a Map to track the latest version of each flow
        Map<String, FlowInterface> latestFlows = new HashMap<>();

        stream.forEach(flow -> {
            String uid = flow.uidWithoutRevision();
            FlowInterface existing = latestFlows.get(uid);

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

    public boolean removeUnwanted(Flow f, Execution execution) {
        // we don't allow recursive
        return !f.uidWithoutRevision().equals(FlowId.uidWithoutRevision(execution));
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

    public Stream<FlowTopology> findDependencies(final String tenant, final String namespace, final String id, boolean destinationOnly, boolean expandAll) {
        if (flowTopologyRepository.isEmpty()) {
            throw noRepositoryException();
        }

        return expandAll ? recursiveFlowTopology(new ArrayList<>(), tenant, namespace, id, destinationOnly) : flowTopologyRepository.get().findByFlow(tenant, namespace, id, destinationOnly).stream();
    }

    private Stream<FlowTopology> recursiveFlowTopology(List<String> visitedTopologies, String tenantId, String namespace, String id, boolean destinationOnly) {
        if (flowTopologyRepository.isEmpty()) {
            throw noRepositoryException();
        }

        var flowTopologies = flowTopologyRepository.get().findByFlow(tenantId, namespace, id, destinationOnly);

        var visitedNodes = new ArrayList<String>();
        visitedNodes.add(id);
        return flowTopologies.stream()
            // ignore already visited topologies
            .filter(x -> !visitedTopologies.contains(x.uid()))
            .flatMap(topology -> {
                visitedTopologies.add(topology.uid());
                Stream<FlowTopology> subTopologies = Stream
                    .of(topology.getDestination(), topology.getSource())
                    // ignore already visited nodes
                    .filter(x -> !visitedNodes.contains(x.getId()))
                    // recursively visit children and parents nodes
                    .flatMap(relationNode -> {
                        visitedNodes.add(relationNode.getId());
                        return recursiveFlowTopology(visitedTopologies, relationNode.getTenantId(), relationNode.getNamespace(), relationNode.getId(), destinationOnly);
                    });
                return Stream.concat(Stream.of(topology), subTopologies);
            });
    }

    private IllegalStateException noRepositoryException() {
        return new IllegalStateException("No repository found. Make sure the `kestra.repository.type` property is set.");
    }
}