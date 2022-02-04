package io.kestra.core.services;

import io.kestra.core.models.flows.Flow;

import java.util.List;
import java.util.function.Consumer;

public interface FlowListenersInterface {
    void run();

    void listen(Consumer<List<Flow>> consumer);

    List<Flow> flows();
}
