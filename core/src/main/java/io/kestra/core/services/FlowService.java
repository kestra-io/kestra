package io.kestra.core.services;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.models.triggers.WorkerTriggerInterface;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.events.TriggerCreated;
import io.kestra.core.scheduler.events.TriggerDeleted;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerFlowRevisionUpdated;
import io.kestra.core.scheduler.events.TriggerUpdated;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.SecretUtils;
import io.kestra.plugin.core.flow.Pause;

import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides business logic for manipulating flow objects.
 */
@Singleton
@Slf4j
public class FlowService {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @Inject
    private Provider<RunContextFactory> runContextFactory; // Lazy init: avoid circular dependency error.

    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private BroadcastQueueInterface<FlowInterface> flowQueue;

    @Inject
    private TriggerEventQueue triggerEventQueue;

    @Inject
    private PluginRegistry pluginRegistry;

    private final ExecutorService executorService;

    @Inject
    public FlowService(ExecutorsUtils executorsUtils) {
        this.executorService = executorsUtils.maxCachedThreadPool(KestraContext.getContext().getAllocatedCpuCores(), "flow-service");
    }

    @PreDestroy
    void close() throws InterruptedException {
        executorService.shutdown();

        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }

    /**
     * Validates and creates the given flow.
     * <p>
     * The validation of the flow is done from the source after injecting all plugin default values.
     *
     * @param flow The flow.
     * @return The created {@link FlowWithSource}.
     */
    public FlowWithSource create(GenericFlow flow) throws FlowProcessingException, QueueException {
        // FIXME validation is done both here and in the repo
        Objects.requireNonNull(flow, "Cannot create null flow");
        if (flow.getSource() == null || flow.getSource().isBlank()) {
            throw new IllegalArgumentException("Cannot create flow with null or blank source");
        }

        // Inject plugin default versions, and perform strict parsing validation (i.e., checking unknown and duplicated properties).
        FlowWithSource parsed = pluginDefaultService.parseFlowWithVersionDefaults(flow.getTenantId(), flow.getSource(), true);

        // Validate Flow with defaults values
        // Do not perform a strict parsing validation to ignore unknown
        // properties that might be injecting through default values.
        modelValidator.validate(pluginDefaultService.injectAllDefaults(parsed, false));

        FlowWithSource created = flowRepository.create(flow);

        // impact downstream consumers: topology, scheduler and flow metastore
        impactDownstreamConsumers(created);

        return created;
    }

    /**
     * Validates and creates the given flow.
     * <p>
     * The validation of the flow is done from the source after injecting all plugin default values.
     *
     * @param flow The flow.
     * @return The created {@link FlowWithSource}.
     */
    public FlowWithSource update(GenericFlow flow, FlowInterface previous) throws FlowProcessingException, QueueException {
        // FIXME validation is done both here and in the repo
        Objects.requireNonNull(flow, "Cannot create null flow");
        if (flow.getSource() == null || flow.getSource().isBlank()) {
            throw new IllegalArgumentException("Cannot create flow with null or blank source");
        }
        Objects.requireNonNull(previous, "Cannot update a flow with null previous");

        // Inject plugin default versions, and perform strict parsing validation (i.e., checking unknown and duplicated properties).
        FlowWithSource parsed = pluginDefaultService.parseFlowWithVersionDefaults(flow.getTenantId(), flow.getSource(), true);

        // Validate Flow with defaults values
        // Do not perform a strict parsing validation to ignore unknown
        // properties that might be injecting through default values.
        modelValidator.validate(pluginDefaultService.injectAllDefaults(parsed, false));

        FlowWithSource updated = flowRepository.update(flow, previous);

        // impact downstream consumers: topology, scheduler and flow metastore
        impactDownstreamConsumers(updated);

        return updated;
    }

    /**
     * Delete a flow.
     */
    public FlowWithSource delete(FlowWithSource flow) {
        FlowWithSource deleted = flowRepository.delete(flow);

        // impact downstream consumers: topology, scheduler and flow metastore
        try {
            impactDownstreamConsumers(deleted);
        } catch (QueueException e) {
            // TODO tmp fix for git-ee plugin, but we handle in some way this exception in the whole service
            throw new RuntimeException(e);
        }

        return deleted;
    }

    private void impactDownstreamConsumers(FlowWithSource flow) throws QueueException {
        // update the topology asynchronously
        executorService.submit(() -> updateTopology(flow));

        // compute triggers events for the Scheduler
        recomputeTriggers(flow);

        // send it to the flow queue for the flow metastore
        flowQueue.emit(flow);
    }

    private void updateTopology(FlowWithSource flow) {
        flowTopologyRepository.save(
            flow,
            (flow.isDeleted() ? Stream.<FlowTopology> empty()
                : flowTopologyService
                    .topology(
                        flow,
                        flowRepository.findAllWithSource(flow.getTenantId())
                    ))
                .distinct()
                .toList()
        );
    }

    private void recomputeTriggers(FlowWithSource flow) {
        var previous = flow.getRevision() <= 1 ? null : flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision() - 1)).orElse(null);

        // If the previous revision was soft-deleted, the scheduler already dropped its
        // trigger state on TriggerDeleted. Re-creating the flow must emit TriggerCreated
        // so the state is rebuilt; treat it as if there was no previous.
        if (previous != null && previous.isDeleted()) {
            previous = null;
        }

        if (flow.isDeleted()) {
            ListUtils.emptyOnNull(flow.getTriggers()).forEach(
                trigger -> sendTriggerEvent(new TriggerDeleted(TriggerId.of(flow, trigger)))
            );
            return;
        }

        if (previous != null) {
            FlowService.findRemovedTrigger(flow, previous).forEach(
                trigger -> sendTriggerEvent(new TriggerDeleted(TriggerId.of(flow, trigger)))
            );

            if (flow.isDeleted()) {
                return;
            }
        }

        if (previous != null && !Objects.equals(previous.getRevision(), flow.getRevision())) {
            FlowService.findUpdatedTrigger(flow, previous)
                .stream()
                .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                .forEach(
                    trigger -> sendTriggerEvent(new TriggerUpdated(TriggerId.of(flow, trigger), flow.getRevision()))
                );
            FlowService.findNewTrigger(flow, previous)
                .stream()
                .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                .forEach(
                    trigger -> sendTriggerEvent(new TriggerCreated(TriggerId.of(flow, trigger), flow.getRevision()))
                );
            FlowService.findUnchangedTrigger(flow, previous)
                .stream()
                .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                .forEach(
                    trigger -> sendTriggerEvent(new TriggerFlowRevisionUpdated(TriggerId.of(flow, trigger), flow.getRevision()))
                );
            return;
        }

        if (flow.getTriggers() != null) {
            flow.getTriggers()
                .stream()
                .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                .forEach(
                    trigger -> sendTriggerEvent(new TriggerCreated(TriggerId.of(flow, trigger), flow.getRevision()))
                );
        }
    }

    private void sendTriggerEvent(TriggerEvent event) {
        this.triggerEventQueue.send(event);
    }

    private static String formatValidationError(String message) {
        if (message.startsWith("Illegal flow source:")) {
            // Already formatted by YamlParser, return as-is
            return message;
        } else if (message.startsWith(":")) {
            message = message.substring(1);
        }
        // For other validation errors, provide context
        return "Validation error: " + message;
    }

    /**
     * Evaluates all checks defined in the given flow using the provided inputs.
     * <p>
     * Each check's {@link Check#getWhen()} is evaluated in the context of the flow.
     * If a condition evaluates to {@code false} or fails to evaluate due to a
     * variable error, the corresponding {@link Check} is added to the returned list.
     * </p>
     *
     * @param flow the flow containing the checks to evaluate
     * @param inputs the input values used when evaluating the conditions
     * @return a list of checks whose conditions evaluated to {@code false} or failed to evaluate
     */
    public List<Check> getFailedChecks(Flow flow, Map<String, Object> inputs) {
        if (!ListUtils.isEmpty(flow.getChecks())) {
            RunContext runContext = runContextFactory.get().of(flow, Map.of("inputs", inputs));
            List<Check> falseConditions = new ArrayList<>();
            for (Check check : flow.getChecks()) {
                try {
                    boolean result = Boolean.TRUE.equals(runContext.renderTyped(check.getWhen()));
                    if (!result) {
                        falseConditions.add(check);
                    }
                } catch (IllegalVariableEvaluationException e) {
                    log.debug(
                        "[tenant: {}] [namespace: {}] [flow: {}] Failed to evaluate check condition. Cause.: {}",
                        flow.getTenantId(),
                        flow.getNamespace(),
                        flow.getId(),
                        e.getMessage(),
                        e
                    );
                    falseConditions.add(
                        Check
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
     * Validates the given flow sources.
     *
     * @param tenantId The tenant identifier.
     * @param flowSources The flow sources to validate.
     * @return The list of validation constraint violations.
     */
    public List<ValidateConstraintViolation> validate(final String tenantId, final List<FlowSource> flowSources) {
        AtomicInteger index = new AtomicInteger(0);
        List<ValidateConstraintViolation> constraints = new ArrayList<>();
        flowSources.forEach(flowSource ->
        {
            ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> constraintsBuilder = ValidateConstraintViolation.builder();
            constraintsBuilder.index(index.getAndIncrement());
            constraintsBuilder.filename(flowSource.filename());

            try {
                String source = flowSource.content();
                FlowWithSource flow = pluginDefaultService.parseFlowWithVersionDefaults(tenantId, source, true);

                Integer sentRevision = flow.getRevision();
                if (sentRevision != null) {
                    Integer lastRevision = Optional.ofNullable(flowRepository.lastRevision(tenantId, flow.getNamespace(), flow.getId())).orElse(0);
                    constraintsBuilder.outdated(!sentRevision.equals(lastRevision + 1));
                }

                constraintsBuilder.deprecationPaths(deprecationPaths(flow));
                constraintsBuilder.warnings(warnings(flow, tenantId));
                constraintsBuilder.infos(relocations(source).stream().map(relocation -> relocation.from() + " is replaced by " + relocation.to()).toList());
                constraintsBuilder.flow(flow.getId());
                constraintsBuilder.namespace(flow.getNamespace());

                // Do not perform a strict parsing validation to ignore unknown
                // properties that might be injecting through default values.
                modelValidator.validate(pluginDefaultService.injectAllDefaults(flow, false));
            } catch (ConstraintViolationException e) {
                String friendlyMessage = formatValidationError(e.getMessage());
                constraintsBuilder.constraints(friendlyMessage);
            } catch (FlowProcessingException e) {
                if (e.getCause() instanceof ConstraintViolationException cve) {
                    String friendlyMessage = formatValidationError(cve.getMessage());
                    constraintsBuilder.constraints(friendlyMessage);
                } else {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    constraintsBuilder.constraints("Unable to validate the flow: " + cause.getMessage());
                }
            } catch (RuntimeException e) {
                // In case of any error, we add a validation violation so the error is displayed in the UI.
                // We may change that by throwing an internal error and handle it in the UI, but this should not occur except for rare cases
                // in dev like incompatible plugin versions.
                log.error("Unable to validate the flow", e);
                constraintsBuilder.constraints("Unable to validate the flow: " + e.getMessage());
            }

            constraints.add(constraintsBuilder.build());
        });

        return constraints;
    }

    public FlowWithSource importFlow(String tenantId, String source) throws FlowProcessingException {
        return this.importFlow(tenantId, source, false);
    }

    public FlowWithSource importFlow(String tenantId, String source, boolean dryRun) throws FlowProcessingException {
        final GenericFlow flow = GenericFlow.fromYaml(tenantId, source);

        Optional<FlowWithSource> maybeExisting = flowRepository.findByIdWithSource(
            flow.getTenantId(),
            flow.getNamespace(),
            flow.getId(),
            Optional.empty(),
            true
        );

        // Inject default plugin 'version' props before converting
        // to flow to correctly resolve all plugin type.
        FlowWithSource flowToImport = pluginDefaultService.injectVersionDefaults(flow, false, true);

        if (dryRun) {
            return maybeExisting
                .map(
                    previous -> previous.isSameWithSource(flowToImport) && !previous.isDeleted() ? previous
                        : FlowWithSource.of(flowToImport.toBuilder().revision(previous.getRevision() + 1).build(), source)
                )
                .orElseGet(() -> FlowWithSource.of(flowToImport, source).toBuilder().tenantId(tenantId).revision(1).build());
        } else {
            return maybeExisting
                .map(previous -> flowRepository.update(flow, previous))
                .orElseGet(() -> flowRepository.create(flow));
        }
    }

    public List<FlowWithSource> findByNamespaceWithSource(String tenantId, String namespace) {
        return flowRepository.findByNamespaceWithSource(tenantId, namespace);
    }

    public List<Flow> findAll(String tenantId) {
        return flowRepository.findAll(tenantId);
    }

    public List<Flow> findByNamespace(String tenantId, String namespace) {
        return flowRepository.findByNamespace(tenantId, namespace);
    }

    public Optional<Flow> findById(String tenantId, String namespace, String flowId) {
        return flowRepository.findById(tenantId, namespace, flowId);
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
        flowTriggers.forEach(flowTrigger ->
        {
            if (ListUtils.isEmpty(flowTrigger.getDependsOn())
                && (flowTrigger.getWhen() == null || "true".equals(flowTrigger.getWhen()))) {
                warnings.add(
                    "This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the dependsOn property to the Flow trigger '" + flowTrigger.getId()
                        + "'."
                );
            }
        });

        // add warning for runnable properties (timeout, workerGroup, taskCache) when used not in a runnable
        flow.allTasksWithChilds().forEach(task ->
        {
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

        // warn when @PluginProperty(secret=true) fields have plain-text values
        flow.allTasksWithChilds().forEach(task ->
            SecretUtils.validateSecretFields(task)
                .forEach(msg -> warnings.add("Task '" + task.getId() + "': " + msg)));
        ListUtils.emptyOnNull(flow.getTriggers()).forEach(trigger ->
            SecretUtils.validateSecretFields(trigger)
                .forEach(msg -> warnings.add("Trigger '" + trigger.getId() + "': " + msg)));

        return warnings;
    }

    public List<Relocation> relocations(String flowSource) {
        try {
            Map<String, Class<?>> aliases = pluginRegistry.plugins().stream()
                .flatMap(plugin -> plugin.getAliases().values().stream())
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, duplicate) -> existing
                    )
                );
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

        subFlows.forEach(subflow ->
        {
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

    public record Relocation(String from, String to) {
    }

    public record TaskDeprecation(String taskId, String taskType, @Nullable String replacement) {
    }

    public List<TaskDeprecation> findDeprecatedTasks(Flow flow) {
        return flow.allTasksWithChilds().stream()
            .flatMap(task ->
            {
                String taskType = task.getType();
                return pluginRegistry.findMetadataByIdentifier(taskType)
                    .flatMap(metadata ->
                    {
                        // Case 1: task uses a deprecated alias name
                        if (metadata.alias() != null) {
                            boolean replacementDeprecated = Plugin.isDeprecated(metadata.type());
                            String replacement = replacementDeprecated ? null : metadata.type().getName();
                            return Optional.of(new TaskDeprecation(task.getId(), taskType, replacement));
                        }
                        // Case 2: task class itself is annotated @Deprecated
                        if (Plugin.isDeprecated(metadata.type())) {
                            return Optional.of(new TaskDeprecation(task.getId(), taskType, null));
                        }
                        return Optional.empty();
                    })
                    .stream();
            })
            .toList();
    }

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
                List<Relocation> listAliases = value.stream().flatMap(item ->
                {
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
                .flatMap(method ->
                {
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

        stream.forEach(flow ->
        {
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
            .filter(
                p -> ListUtils.emptyOnNull(flow.getTriggers())
                    .stream()
                    .noneMatch(c -> c.getId().equals(p.getId()))
            )
            .toList();
    }

    public static List<AbstractTrigger> findUpdatedTrigger(Flow flow, Flow previous) {
        return ListUtils.emptyOnNull(flow.getTriggers())
            .stream()
            .filter(
                oldTrigger -> ListUtils.emptyOnNull(previous.getTriggers())
                    .stream()
                    .anyMatch(trigger -> trigger.getId().equals(oldTrigger.getId()) && !EqualsBuilder.reflectionEquals(trigger, oldTrigger))
            )
            .toList();
    }

    public static List<AbstractTrigger> findNewTrigger(Flow flow, Flow previous) {
        return ListUtils.emptyOnNull(flow.getTriggers())
            .stream()
            .filter(
                oldTrigger -> ListUtils.emptyOnNull(previous.getTriggers())
                    .stream()
                    .noneMatch(trigger -> trigger.getId().equals(oldTrigger.getId()))
            )
            .toList();
    }

    public static List<AbstractTrigger> findUnchangedTrigger(Flow flow, Flow previous) {
        return ListUtils.emptyOnNull(flow.getTriggers())
            .stream()
            .filter(
                current -> ListUtils.emptyOnNull(previous.getTriggers())
                    .stream()
                    .anyMatch(prev -> prev.getId().equals(current.getId()) && EqualsBuilder.reflectionEquals(prev, current))
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
        return flowRepository.findByNamespacePrefix(tenantId, namespacePrefix);
    }

    /**
     * Gets the executable flow for the given namespace, id, and revision.
     * Warning: this method bypasses ACL so someone with only execution right can create a flow execution
     *
     * @param tenant Rhe tenant ID.
     * @param namespace The flow's namespace.
     * @param id The flow's ID.
     * @param revision The flow's revision.
     * @return The {@link Flow}.
     * @throws NoSuchElementException if the requested flow does not exist.
     * @throws IllegalStateException if the requested flow is not executable.
     */
    public Flow getFlowIfExecutableOrThrow(final String tenant, final String namespace, final String id, final Optional<Integer> revision) {
        Optional<Flow> optional = flowRepository.findByIdWithoutAcl(tenant, namespace, id, revision);
        if (optional.isEmpty()) {
            throw new NoSuchElementException("Requested Flow is not found.");
        }

        Flow flow = optional.get();
        if (flow.isDisabled()) {
            throw new IllegalStateException("Requested Flow is disabled.");
        }

        if (flow instanceof FlowWithException fwe) {
            throw new IllegalStateException("Requested Flow is not valid. Error: " + fwe.getException());
        }
        return flow;
    }

    public Stream<FlowTopology> findDependencies(final String tenant, final String namespace, final String id, boolean destinationOnly, boolean expandAll) {
        return expandAll ? recursiveFlowTopology(new ArrayList<>(), tenant, namespace, id, destinationOnly)
            : flowTopologyRepository.findByFlow(tenant, namespace, id, destinationOnly).stream();
    }

    private Stream<FlowTopology> recursiveFlowTopology(List<String> visitedTopologies, String tenantId, String namespace, String id, boolean destinationOnly) {
        var flowTopologies = flowTopologyRepository.findByFlow(tenantId, namespace, id, destinationOnly);

        var visitedNodes = new ArrayList<String>();
        visitedNodes.add(id);
        return flowTopologies.stream()
            // ignore already visited topologies
            .filter(x -> !visitedTopologies.contains(x.uid()))
            .flatMap(topology ->
            {
                visitedTopologies.add(topology.uid());
                Stream<FlowTopology> subTopologies = Stream
                    .of(topology.getDestination(), topology.getSource())
                    // ignore already visited nodes
                    .filter(x -> !visitedNodes.contains(x.getId()))
                    // recursively visit children and parents nodes
                    .flatMap(relationNode ->
                    {
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
