package org.kestra.core.services;

import org.kestra.core.models.flows.Flow;

import java.util.List;
import java.util.function.Consumer;

public interface FlowListenersInterface {
    void listen(Consumer<List<Flow>> consumer);

    List<Flow> flows();
}
