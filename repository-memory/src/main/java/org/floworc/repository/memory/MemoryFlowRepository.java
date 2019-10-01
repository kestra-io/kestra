package org.floworc.repository.memory;

import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.FlowRepositoryInterface;

import javax.inject.Singleton;
import java.util.*;

@Singleton
public class MemoryFlowRepository implements FlowRepositoryInterface {
    private Map<String, Flow> flows = new HashMap<>();

    @Override
    public Optional<Flow> findById(String id) {
        return this.flows.containsKey(id) ? Optional.of(this.flows.get(id)) : Optional.empty();
    }

    @Override
    public List<Flow> findAll() {
        return new ArrayList<>(this.flows.values());
    }

    @Override
    public void save(Flow flow) {
        this.flows.put(flow.getId(), flow);
    }

    @Override
    public void insert(Flow flow) {
        if (this.flows.containsKey(flow.getId())) {
            throw new IllegalStateException("Flow " + flow.getId() + " already exists");
        }

        this.flows.put(flow.getId(), flow);
    }

    @Override
    public void update(Flow flow) {
        if (!this.flows.containsKey(flow.getId())) {
            throw new IllegalStateException("Flow " + flow.getId() + " already exists");
        }

        this.flows.put(flow.getId(), flow);
    }

    @Override
    public void delete(Flow flow) {
        if (!this.flows.containsKey(flow.getId())) {
            throw new IllegalStateException("Flow " + flow.getId() + " already exists");
        }

        this.flows.remove(flow.getId());
    }
}
