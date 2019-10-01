package org.floworc.core.repositories;

import org.floworc.core.models.flows.Flow;

import java.util.List;
import java.util.Optional;

public interface FlowRepositoryInterface {
    Optional<Flow> findById(String id);

    List<Flow> findAll();

    void save(Flow flow);

    void insert(Flow flow);

    void update(Flow flow);

    void delete(Flow flow);
}
