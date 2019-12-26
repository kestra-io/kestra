package org.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.Execution;

import java.util.Optional;

public interface ExecutionRepositoryInterface {
    Optional<Execution> findById(String id);

    ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable);

    ArrayListTotal<Execution> find(String query, Pageable pageable);

    Execution save(Execution flow);
}
