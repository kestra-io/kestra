package io.kestra.core.runners;

import io.kestra.core.models.flows.Flow;

import java.util.Optional;

public interface FlowExecutorInterface {
    Flow findById(String namespace, String id, Optional<Integer> revision);
}
