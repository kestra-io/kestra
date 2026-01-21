package io.kestra.core.services;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.models.triggers.WorkerTriggerInterface;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.TriggerEventQueue;
import io.kestra.core.scheduler.events.TriggerCreated;
import io.kestra.core.scheduler.events.TriggerDeleted;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerUpdated;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.ListUtils;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private DispatchQueueInterface<FlowInterface> flowQueue;

    @Inject
    private TriggerEventQueue triggerEventQueue;

    @Inject
    private FlowValidationService flowValidationService;

    private final ExecutorService executorService;

    @Inject
    public FlowService(ExecutorsUtils executorsUtils) {
        this.executorService = executorsUtils.maxCachedThreadPool(Runtime.getRuntime().availableProcessors(), "flow-service");
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
     * @param flow             The flow.
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
     * @param flow             The flow.
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
    public FlowWithSource delete(FlowWithSource flow) throws QueueException {
        FlowWithSource deleted = flowRepository.delete(flow);

        // impact downstream consumers: topology, scheduler and flow metastore
        impactDownstreamConsumers(deleted);

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
            (flow.isDeleted() ?
                Stream.<FlowTopology>empty() :
                flowTopologyService
                    .topology(
                        flow,
                        flowRepository.findAllWithSource(flow.getTenantId())
                    )
            )
                .distinct()
                .toList()
        );
    }

    private void recomputeTriggers(FlowWithSource flow) {
        var previous = flow.getRevision() <= 1 ? null : flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision() - 1)).orElse(null);

        if (flow.isDeleted() || previous != null) {
            List<AbstractTrigger> triggersDeleted = flow.isDeleted() ?
                ListUtils.emptyOnNull(flow.getTriggers()) :
                FlowService.findRemovedTrigger(flow, previous);

            triggersDeleted.forEach(trigger ->
                sendTriggerEvent(new TriggerDeleted(TriggerId.of(flow, trigger)))
            );
        }

        if (previous != null && !Objects.equals(previous.getRevision(), flow.getRevision())) {
            FlowService.findUpdatedTrigger(flow, previous)
                .stream()
                .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                .forEach(trigger ->
                    sendTriggerEvent(new TriggerUpdated(TriggerId.of(flow, trigger), flow.getRevision()))
                );
            FlowService.findNewTrigger(flow, previous)
                .stream()
                .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                .forEach(trigger ->
                    sendTriggerEvent(new TriggerUpdated(TriggerId.of(flow, trigger), flow.getRevision()))
                );
            return;
        }

        if (flow.getTriggers() != null) {
            flow.getTriggers()
                .stream()
                .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                .forEach(trigger ->
                    sendTriggerEvent(new TriggerCreated(TriggerId.of(flow, trigger), flow.getRevision()))
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
     * Validates the given flow source. The YAML source can contain one or many objects.
     * <p>
     * Individual validation methods are provided inside the {@link FlowValidationService}.
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
                        Integer lastRevision = Optional.ofNullable(flowRepository.lastRevision(tenantId, flow.getNamespace(), flow.getId()))
                            .orElse(0);
                        validateConstraintViolationBuilder.outdated(!sentRevision.equals(lastRevision + 1));
                    }

                    validateConstraintViolationBuilder.deprecationPaths(flowValidationService.deprecationPaths(flow));
                    validateConstraintViolationBuilder.warnings(flowValidationService.warnings(flow, tenantId));
                    validateConstraintViolationBuilder.infos(flowValidationService.relocations(source).stream().map(relocation -> relocation.from() + " is replaced by " + relocation.to()).toList());
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

        Optional<FlowWithSource> maybeExisting = flowRepository.findByIdWithSource(
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

    public static List<AbstractTrigger> findNewTrigger(Flow flow, Flow previous) {
        return ListUtils.emptyOnNull(flow.getTriggers())
            .stream()
            .filter(oldTrigger -> ListUtils.emptyOnNull(previous.getTriggers())
                .stream()
                .noneMatch(trigger -> trigger.getId().equals(oldTrigger.getId()))
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
     * @param tenant    Rhe tenant ID.
     * @param namespace The flow's namespace.
     * @param id        The flow's ID.
     * @param revision  The flow's revision.
     * @return The {@link Flow}.
     * @throws NoSuchElementException if the requested flow does not exist.
     * @throws IllegalStateException  if the requested flow is not executable.
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

        if (flow instanceof FlowWithException fwe ) {
            throw new IllegalStateException("Requested Flow is not valid. Error: " + fwe.getException());
        }
        return flow;
    }

    public Stream<FlowTopology> findDependencies(final String tenant, final String namespace, final String id, boolean destinationOnly, boolean expandAll) {
        return expandAll ? recursiveFlowTopology(new ArrayList<>(), tenant, namespace, id, destinationOnly) : flowTopologyRepository.findByFlow(tenant, namespace, id, destinationOnly).stream();
    }

    private Stream<FlowTopology> recursiveFlowTopology(List<String> visitedTopologies, String tenantId, String namespace, String id, boolean destinationOnly) {
        var flowTopologies = flowTopologyRepository.findByFlow(tenantId, namespace, id, destinationOnly);

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