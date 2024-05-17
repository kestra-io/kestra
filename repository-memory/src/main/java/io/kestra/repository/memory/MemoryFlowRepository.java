package io.kestra.repository.memory;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowForExecution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.NotImplementedException;

import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;

import java.util.*;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryFlowRepository implements FlowRepositoryInterface {
    private final HashMap<String, Flow> flows = new HashMap<>();
    private final HashMap<String, Flow> revisions = new HashMap<>();
    private final HashMap<String, String> flowSources = new HashMap<>();

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<Flow> flowQueue;

    @Inject
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    private QueueInterface<Trigger> triggerQueue;

    @Inject
    private ApplicationEventPublisher<CrudEvent<Flow>> eventPublisher;

    @Inject
    private ModelValidator modelValidator;

    private static String flowId(Flow flow) {
        return flowId(flow.getTenantId(), flow.getNamespace(), flow.getId());
    }

    private static String flowId(String tenantId, String namespace, String id) {
        return IdUtils.fromParts(
            tenantId,
            namespace,
            id
        );
    }

    @Override
    public Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted) {
        return revision
            .map(integer -> this.findRevisions(tenantId, namespace, id)
                .stream()
                .filter(flow -> flow.getRevision().equals(integer))
                .map(FlowWithSource::toFlow)
                .findFirst()
            )
            .orElseGet(() -> this.flows.containsKey(flowId(tenantId, namespace, id)) ?
                Optional.of(this.flows.get(flowId(tenantId, namespace, id))) :
                Optional.empty()
            );
    }

    @Override
    public Optional<Flow> findByIdWithoutAcl(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return findById(tenantId, namespace, id, revision, false);
    }

    private Optional<String> findSourceById(String tenantId, String namespace, String id) {
        return this.flowSources.containsKey(flowId(tenantId, namespace, id)) ?
            Optional.of(this.flowSources.get(flowId(tenantId, namespace, id))) :
            Optional.empty();
    }

    @Override
    public Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted) {
        Optional<Flow> flow = findById(tenantId, namespace, id, revision);
        Optional<String> sourceCode = findSourceById(tenantId, namespace, id);
        if (flow.isPresent() && sourceCode.isPresent()) {
            return Optional.of(FlowWithSource.of(flow.get(), FlowService.cleanupSource(sourceCode.get())));
        }

        return Optional.empty();
    }

    @Override
    public List<FlowWithSource> findRevisions(String tenantId, String namespace, String id) {
        return revisions
            .values()
            .stream()
            .filter(flow -> flow.getNamespace().equals(namespace) && flow.getId().equals(id))
            .filter(flow -> (tenantId == null && flow.getTenantId() == null) || (tenantId != null && tenantId.equals(flow.getTenantId())))
            .map(flow -> FlowWithSource.of(flow, flow.generateSource()))
            .sorted(Comparator.comparingInt(Flow::getRevision))
            .collect(Collectors.toList());
    }

    @Override
    public Integer lastRevision(String tenantId, String namespace, String id) {
        List<FlowWithSource> flowRevisions = findRevisions(tenantId, namespace, id);
        return flowRevisions.isEmpty() ? null : flowRevisions.get(flowRevisions.size() - 1).getRevision();
    }

    @Override
    public List<Flow> findAll(String tenantId) {
        return flows.values().stream()
            .filter(flow -> (tenantId == null && flow.getTenantId() == null) || (tenantId != null && tenantId.equals(flow.getTenantId())))
            .toList();
    }


    @Override
    public List<Flow> findAllForAllTenants() {
        return new ArrayList<>(flows.values());
    }

    @Override
    public List<Flow> findByNamespace(String tenantId, String namespace) {
        return flows.values()
            .stream()
            .filter(flow -> flow.getNamespace().equals(namespace))
            .filter(flow -> (tenantId == null && flow.getTenantId() == null) || (tenantId != null && tenantId.equals(flow.getTenantId())))
            .sorted(Comparator.comparingInt(Flow::getRevision))
            .collect(Collectors.toList());
    }

    @Override
    public List<Flow> findByNamespacePrefix(String tenantId, String namespacePrefix) {
        return flows.values()
            .stream()
            .filter(flow -> flow.getNamespace().equals(namespacePrefix) || flow.getNamespace().startsWith(namespacePrefix + "."))
            .filter(flow -> (tenantId == null && flow.getTenantId() == null) || (tenantId != null && tenantId.equals(flow.getTenantId())))
            .sorted(Comparator.comparingInt(Flow::getRevision))
            .collect(Collectors.toList());
    }

    @Override
    public List<FlowForExecution> findByNamespaceExecutable(String tenantId, String namespace) {
        return List.of();
    }

    @Override
    public List<FlowWithSource> findByNamespaceWithSource(String tenantId, String namespace) {
        return flows.values()
            .stream()
            .filter(flow -> flow.getNamespace().equals(namespace))
            .filter(flow -> (tenantId == null && flow.getTenantId() == null) || (tenantId != null && tenantId.equals(flow.getTenantId())))
            .sorted(Comparator.comparingInt(Flow::getRevision))
            .map(flow -> FlowWithSource.of(flow, FlowService.cleanupSource(findSourceById(tenantId, namespace, flow.getId()).get())))
            .collect(Collectors.toList());
    }

    @Override
    public ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    ) {
        if (pageable.getNumber() < 1) {
            throw new ValueException("Page cannot be < 1");
        }

        //TODO Non used query, just returns all flow and filter by namespace if set
        List<Flow> results = flows.values()
            .stream()
            .filter(flow -> namespace == null || flow.getNamespace().equals(namespace) || flow.getNamespace().startsWith(namespace + "."))
            .filter(flow -> (tenantId == null && flow.getTenantId() == null) || (tenantId != null && tenantId.equals(flow.getTenantId())))
            .filter(flow -> labels == null || labels.isEmpty() || (flow.getLabels() != null && flow.getLabels().stream().anyMatch(label -> labels.containsKey(label.key()) && labels.get(label.key()).equals(label.value()))))
            .collect(Collectors.toList());
        return ArrayListTotal.of(pageable, results);
    }

    @Override
    public List<FlowWithSource> findWithSource(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    ) {
        //TODO Non used query, just returns all flow and filter by namespace if set
        return flows.values()
            .stream()
            .filter(flow -> namespace == null || flow.getNamespace().equals(namespace) || flow.getNamespace().startsWith(namespace + "."))
            .filter(flow -> (tenantId == null && flow.getTenantId() == null) || (tenantId != null && tenantId.equals(flow.getTenantId())))
            .filter(flow -> labels == null || labels.isEmpty() || (flow.getLabels() != null && flow.getLabels().stream().anyMatch(label -> labels.containsKey(label.key()) && labels.get(label.key()).equals(label.value()))))
            .sorted(Comparator.comparingInt(Flow::getRevision))
            .map(flow -> findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision())).get())
            .collect(Collectors.toList());
    }

    @Override
    public ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, @Nullable String tenantId, @Nullable String namespace) {
        throw new NotImplementedException();
    }

    @Override
    public FlowWithSource create(Flow flow, String flowSource, Flow flowWithDefaults) {
        if (this.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Flow id already exists",
                flow,
                Flow.class,
                "flow.id",
                flow.getId()
            )));
        }

        // Check flow with defaults injected
        modelValidator.validate(flowWithDefaults);

        return this.save(flow, CrudEventType.CREATE, flowSource);
    }

    @Override
    public FlowWithSource update(Flow flow, Flow previous, String flowSource, Flow flowWithDefaults) throws ConstraintViolationException {
        // Check flow with defaults injected
        modelValidator.validate(flowWithDefaults);

        // control if update is valid
        Optional<ConstraintViolationException> checkUpdate = previous.validateUpdate(flowWithDefaults);
        if (checkUpdate.isPresent()) {
            throw checkUpdate.get();
        }

        FlowService
            .findRemovedTrigger(flow, previous)
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        return this.save(flow, CrudEventType.UPDATE, flowSource);
    }

    private FlowWithSource save(Flow flow, CrudEventType crudEventType, String flowSource) throws ConstraintViolationException {
        if (flow instanceof FlowWithSource) {
            flow = ((FlowWithSource) flow).toFlow();
        }

        // flow exists, return it
        Optional<Flow> exists = this.findById(flow.getTenantId(), flow.getNamespace(), flow.getId());
        Optional<String> existsSource = this.findSourceById(flow.getTenantId(), flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().equalsWithoutRevision(flow) && existsSource.isPresent() && FlowService.cleanupSource(existsSource.get()).equals(FlowService.cleanupSource(flowSource))) {
            return FlowWithSource.of(exists.get(), existsSource.get());
        }

        List<FlowWithSource> revisions = this.findRevisions(flow.getTenantId(), flow.getNamespace(), flow.getId());

        if (!revisions.isEmpty()) {
            flow = flow.toBuilder().revision(revisions.get(revisions.size() - 1).getRevision() + 1).build();
        } else {
            flow = flow.toBuilder().revision(1).build();
        }

        this.flows.put(flowId(flow), flow);
        this.revisions.put(flow.uid(), flow);
        this.flowSources.put(flowId(flow), flowSource);

        flowQueue.emit(flow);
        eventPublisher.publishEvent(new CrudEvent<>(flow, crudEventType));

        return FlowWithSource.of(flow, flowSource);
    }

    @Override
    public Flow delete(Flow flow) {
        if (flow instanceof FlowWithSource) {
            flow = ((FlowWithSource) flow).toFlow();
        }

        if (this.findById(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision())).isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        Flow deleted = flow.toDeleted();

        flowQueue.emit(deleted);
        this.flows.remove(flowId(deleted));
        this.revisions.put(deleted.uid(), deleted);

        Flow finalFlow = flow;
        ListUtils.emptyOnNull(flow.getTriggers())
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(finalFlow, abstractTrigger)));

        eventPublisher.publishEvent(new CrudEvent<>(flow, CrudEventType.DELETE));

        return deleted;
    }

    @Override
    public List<String> findDistinctNamespace(String tenantId) {
        HashSet<String> namespaces = new HashSet<>();
        for (Flow f : this.findAll(tenantId)) {
            namespaces.add(f.getNamespace());
        }

        ArrayList<String> namespacesList = new ArrayList<>(namespaces);
        Collections.sort(namespacesList);
        return new ArrayList<>(namespacesList);
    }

    @Override
    public List<String> findDistinctNamespaceExecutable(String tenantId) {
        return List.of();
    }
}
