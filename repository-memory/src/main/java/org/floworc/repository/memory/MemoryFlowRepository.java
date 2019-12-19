package org.floworc.repository.memory;

import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.ArrayListTotal;
import org.floworc.core.repositories.FlowRepositoryInterface;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryFlowRepository implements FlowRepositoryInterface {
    private HashMap<String, HashMap<String, Map<Integer, Flow>>> flows = new HashMap<>();

    @Override
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        return this.getNamespace(namespace)
            .flatMap(e -> this.getFlows(e, id))
            .flatMap(e -> revision
                .map(current -> this.getRevision(e, current))
                .orElse(this.getLastRevision(e))
            );
    }

    @Override
    public List<Flow> findRevisions(String namespace, String id) {
        if (!this.flows.containsKey(namespace)) {
            this.flows.put(namespace, new HashMap<>());
        }

        HashMap<String, Map<Integer, Flow>> namespaces = this.flows.get(namespace);

        if (!namespaces.containsKey(id)) {
            namespaces.put(id, new HashMap<>());
        }

        Map<Integer, Flow> revisions = namespaces.get(id);

        return new ArrayList<>(revisions.values());
    }

    private Optional<HashMap<String, Map<Integer, Flow>>> getNamespace(String namespace) {
        return this.flows.containsKey(namespace) ? Optional.of(this.flows.get(namespace)) : Optional.empty();
    }

    private Optional<Map<Integer, Flow>> getFlows(HashMap<String, Map<Integer, Flow>> map, String id) {
        return map.containsKey(id) ? Optional.of(map.get(id)) : Optional.empty();
    }

    private Optional<Flow> getRevision(Map<Integer, Flow> map, Integer revision) {
        return map.containsKey(revision) ? Optional.of(map.get(revision)) : Optional.empty();
    }

    @SuppressWarnings({"ComparatorMethodParameterNotUsed"})
    private Optional<Flow> getLastRevision(Map<Integer, Flow> map) {
        return map
            .entrySet()
            .stream()
            .max((a, entry2) -> a.getKey() > entry2.getKey() ? 1 : -1)
            .map(Map.Entry::getValue);
    }

    @Override
    public List<Flow> findAll() {
        return this.flows
            .entrySet()
            .stream()
            .flatMap(e -> e.getValue()
                .entrySet()
                .stream()
                .flatMap(f -> this.getLastRevision(f.getValue()).stream())
            )
            .collect(Collectors.toList());
    }

    @Override
    public ArrayListTotal<Flow> find(String query, Pageable pageable) {
        //TODO Non used query, returns just all at the moment
        if (pageable.getNumber() < 1) {
            throw new ValueException("Page cannot be < 1");
        }

        List<Flow> flows = this.findAll();

        //handles pagination
        int from = (pageable.getNumber() - 1) * pageable.getSize();
        int to = from + pageable.getSize();
        int size = flows.size();
        to = to >= size ? size : to;
        from = from >= size ? size : from;
        return new ArrayListTotal<Flow>(flows.subList(from, to), flows.size());
    }

    @Override
    public ArrayListTotal<Flow> findByNamespace(String namespace, Pageable pageable) {
        if (pageable.getNumber() < 1) {
            throw new ValueException("Page cannot be < 1");
        }

        List<Flow> flows = this.flows
            .entrySet()
            .stream()
            .flatMap(e -> e.getValue()
                .entrySet()
                .stream()
                .flatMap(f -> this.getLastRevision(f.getValue()).stream())
            )
            .filter(f -> f.getNamespace().equals(namespace))
            .collect(Collectors.toList());

        //handles pagination
        int from = (pageable.getNumber() - 1) * pageable.getSize();
        int to = from + pageable.getSize();
        int size = flows.size();
        to = to >= size ? size : to;
        from = from >= size ? size : from;
        return new ArrayListTotal<Flow>(flows.subList(from, to), flows.size());
    }

    @SuppressWarnings("ComparatorMethodParameterNotUsed")
    @Override
    public Flow save(Flow flow) {
        Optional<Flow> exists = this.exists(flow);
        if (exists.isPresent()) {
            return exists.get();
        }

        if (!this.flows.containsKey(flow.getNamespace())) {
            this.flows.put(flow.getNamespace(), new HashMap<>());
        }
        HashMap<String, Map<Integer, Flow>> namespace = this.flows.get(flow.getNamespace());

        if (!namespace.containsKey(flow.getId())) {
            namespace.put(flow.getId(), new HashMap<>());
        }
        Map<Integer, Flow> revisions = namespace.get(flow.getId());

        if (flow.getRevision() != null) {
            revisions.put(flow.getRevision(), flow);

            return flow;
        }

        Optional<Integer> max = revisions
            .entrySet()
            .stream()
            .max((a, b) -> a.getKey() > b.getKey() ? 1 : -1)
            .map(Map.Entry::getKey);

        Flow saved = flow.withRevision(max.map(integer -> integer + 1).orElse(1));

        revisions.put(saved.getRevision(), saved);

        return saved;
    }

    @Override
    public void delete(Flow flow) {
        if (this.findById(flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision())).isEmpty()) {
            throw new IllegalStateException("Flow " + flow.getId() + " doesn't exists");
        }

        this.getNamespace(flow.getNamespace())
            .flatMap(e -> this.getFlows(e, flow.getId()))
            .ifPresent(e -> {
                e.remove(flow.getRevision());
            });
    }

    @Override
    public ArrayListTotal<String> findNamespaces(Optional<String> prefix) {
        HashSet<String> namespaces = new HashSet<String>();
        for (Flow f : this.findAll()) {
            if (f.getNamespace().startsWith(prefix.orElse(""))) {
                namespaces.add(f.getNamespace());
            }
        }
        ArrayList<String> namespacesList = new ArrayList<String>(namespaces);
        Collections.sort(namespacesList);
        return new ArrayListTotal<String>(namespacesList, namespacesList.size());
    }
}
