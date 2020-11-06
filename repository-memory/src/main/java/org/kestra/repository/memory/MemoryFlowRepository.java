package org.kestra.repository.memory;

import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.FlowRepositoryInterface;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryFlowRepository implements FlowRepositoryInterface {
    private final HashMap<String, Flow> flows = new HashMap<>();
    private final HashMap<String, Flow> revisions = new HashMap<>();

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<Flow> flowQueue;

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
    public List<Flow> findRevisions(String namespace, String id) {
        return revisions
            .values()
            .stream()
            .filter(flow -> flow.getNamespace().equals(namespace) && flow.getId().equals(id))
            .collect(Collectors.toList());
    }

    @Override
    public List<Flow> findAll() {
        return new ArrayList<>(flows.values());
    }

    @Override
    public List<Flow> findByNamespace(String namespace) {
        return flows.values()
            .stream()
            .filter(flow -> flow.getNamespace().equals(namespace))
            .collect(Collectors.toList());
    }

    public ArrayListTotal<Flow> find(String query, Pageable pageable) {
        //TODO Non used query, returns just all at the moment
        if (pageable.getNumber() < 1) {
            throw new ValueException("Page cannot be < 1");
        }

        return ArrayListTotal.of(pageable, this.findAll());
    }

    public Flow create(Flow flow) throws ConstraintViolationException {
        // control if create is valid
        flow.validate()
            .ifPresent(s -> {
                throw s;
            });

        Flow newFlow = this.save(flow);
        flowQueue.emit(newFlow);

        return newFlow;
    }

    public Flow update(Flow flow, Flow previous) throws ConstraintViolationException {
        // control if update is valid
        this
            .findById(previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(flow))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        flowQueue.emit(flow);
        return this.save(flow);
    }

    private Flow save(Flow flow) throws ConstraintViolationException {
        // validate the flow
        modelValidator
            .isValid(flow)
            .ifPresent(s -> {
                throw s;
            });

        // flow exists, return it
        Optional<Flow> exists = this.findById(flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().equalsWithoutRevision(flow)) {
            return exists.get();
        }

        Optional<Flow> current = this.findById(flow.getNamespace(), flow.getId());

        if (current.isPresent()) {
            flow = flow.withRevision(current.get().getRevision() + 1);
        } else if (flow.getRevision() == null) {
            flow = flow.withRevision(1);
        }

        this.flows.put(flowId(flow), flow);
        this.revisions.put(flow.uid(), flow);

        return flow;
    }

    @Override
    public void delete(Flow flow) {
        if (this.findById(flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision())).isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        flowQueue.emit(flow.toDeleted());
        this.flows.remove(flowId(flow));
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
