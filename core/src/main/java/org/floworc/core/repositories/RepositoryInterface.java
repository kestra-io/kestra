package org.floworc.core.repositories;

import org.floworc.core.models.flows.Flow;

import java.util.List;
import java.util.Optional;

public interface RepositoryInterface {
    Optional<Flow> getFlowById(String id);

    List<Flow> getFlows();
}
