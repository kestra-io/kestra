package io.kestra.repository.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.SearchResult;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import org.apache.commons.lang3.NotImplementedException;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryFlowRepository implements FlowRepositoryInterface {
    private final HashMap<String, Flow> flows = new HashMap<>();
    private final HashMap<String, Flow> revisions = new HashMap<>();
    private final HashMap<String, String> flowSources = new HashMap<>();

    @Inject
    private TaskDefaultService taskDefaultService;
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
        return flowId(flow.getNamespace(), flow.getId());
    }

    private static String flowId(String namespace, String id) {
        return String.join("_", Arrays.asList(
            namespace,
            id
        ));
    }

    @Override
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        return revision
            .map(integer -> this.findRevisions(namespace, id)
                .stream()
                .filter(flow -> flow.getRevision().equals(integer))
                .findFirst()
            )
            .orElseGet(() -> this.flows.containsKey(flowId(namespace, id)) ?
                Optional.of(this.flows.get(flowId(namespace, id))) :
                Optional.empty()
            );
    }

    @Override
    public Optional<String> findSourceById(String namespace, String id, Optional<Integer> revision) {
        return findSourceById(namespace, id);
    }

    @Override
    public Optional<String> findSourceById(String namespace, String id) {

        return this.flowSources.containsKey(flowId(namespace, id)) ?
            Optional.of(this.flowSources.get(flowId(namespace, id))) :
            Optional.empty();
    }

    public Optional<Map<String, Object>> findByIdWithSource(String namespace, String id, Optional<Integer> revision) {
        Optional<Flow> flow = findById(namespace, id, revision);
        Optional<String> sourceCode = findSourceById(namespace, id);
        if (flow.isPresent() && sourceCode.isPresent()) {

            return Optional.of(Map.of("flow", flow.get(), "sourceCode", sourceCode.get()));
        }

        return Optional.empty();
    }


    @Override
    public List<Flow> findRevisions(String namespace, String id) {
        return revisions
            .values()
            .stream()
            .filter(flow -> flow.getNamespace().equals(namespace) && flow.getId().equals(id))
            .sorted(Comparator.comparingInt(Flow::getRevision))
            .collect(Collectors.toList());
    }

    @Override
    public List<Flow> findAll() {
        return new ArrayList<>(flows.values());
    }

    @Override
    public List<Flow> findAllWithRevisions() {
        return new ArrayList<>(revisions.values());
    }

    @Override
    public List<Flow> findByNamespace(String namespace) {
        return flows.values()
            .stream()
            .filter(flow -> flow.getNamespace().equals(namespace))
            .sorted(Comparator.comparingInt(Flow::getRevision))
            .collect(Collectors.toList());
    }

    public ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    ) {
        //TODO Non used query, returns just all at the moment
        if (pageable.getNumber() < 1) {
            throw new ValueException("Page cannot be < 1");
        }

        return ArrayListTotal.of(pageable, this.findAll());
    }

    @Override
    public ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, @Nullable String namespace) {
        throw new NotImplementedException();
    }

    public Flow create(Flow flow) throws ConstraintViolationException {
        // control if create is valid
        taskDefaultService.injectDefaults(flow).validate()
            .ifPresent(s -> {
                throw s;
            });

        return (Flow) this.save(flow, CrudEventType.CREATE, null).get("flow");
    }

    @Override
    public Map<String, Object> create(Flow flow, String flowSource) {
        // control if create is valid
        taskDefaultService.injectDefaults(flow).validate()
            .ifPresent(s -> {
                throw s;
            });

        return this.save(flow, CrudEventType.CREATE, flowSource);
    }

    public Flow update(Flow flow, Flow previous) throws ConstraintViolationException {
        // control if update is valid
        this
            .findById(previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(taskDefaultService.injectDefaults(flow)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        FlowService
            .findRemovedTrigger(flow, previous)
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        return (Flow) this.save(flow, CrudEventType.UPDATE, null).get("flow");
    }

    @Override
    public Map<String, Object> update(Flow flow, Flow previous, String flowSource) throws ConstraintViolationException {
        // control if update is valid
        this
            .findById(previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(taskDefaultService.injectDefaults(flow)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        FlowService
            .findRemovedTrigger(flow, previous)
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        return this.save(flow, CrudEventType.UPDATE, flowSource);
    }

    private Map<String, Object> save(Flow flow, CrudEventType crudEventType, String flowSource) throws ConstraintViolationException {
        // validate the flow
        modelValidator
            .isValid(taskDefaultService.injectDefaults(flow))
            .ifPresent(s -> {
                throw s;
            });

        try {
            flowSource = flowSource != null ? flowSource : JacksonMapper.ofYaml().writeValueAsString(flow);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // flow exists, return it
        Optional<Flow> exists = this.findById(flow.getNamespace(), flow.getId());
        Optional<String> existsSource = this.findSourceById(flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().equalsWithoutRevision(flow) && existsSource.isPresent() && existsSource.get().equals(flowSource)) {
            return Map.of("flow", exists.get(), "sourceCode", existsSource.get());
        }

        List<Flow> revisions = this.findRevisions(flow.getNamespace(), flow.getId());

        if (revisions.size() > 0) {
            flow = flow.withRevision(revisions.get(revisions.size() - 1).getRevision() + 1);
        } else {
            flow = flow.withRevision(1);
        }

        this.flows.put(flowId(flow), flow);
        this.revisions.put(flow.uid(), flow);
        this.flowSources.put(flowId(flow), flowSource);

        flowQueue.emit(flow);
        eventPublisher.publishEvent(new CrudEvent<>(flow, crudEventType));

        return Map.of("flow", flow, "sourceCode", flowSource);
    }

    @Override
    public Flow delete(Flow flow) {
        if (this.findById(flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision())).isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        Flow deleted = flow.toDeleted();

        flowQueue.emit(deleted);
        this.flows.remove(flowId(deleted));
        this.revisions.put(deleted.uid(), deleted);

        ListUtils.emptyOnNull(flow.getTriggers())
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        eventPublisher.publishEvent(new CrudEvent<>(flow, CrudEventType.DELETE));

        return deleted;
    }

    @Override
    public List<String> findDistinctNamespace() {
        HashSet<String> namespaces = new HashSet<>();
        for (Flow f : this.findAll()) {
            namespaces.add(f.getNamespace());
        }

        ArrayList<String> namespacesList = new ArrayList<>(namespaces);
        Collections.sort(namespacesList);
        return new ArrayList<>(namespacesList);
    }
}
