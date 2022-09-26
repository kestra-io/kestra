package io.kestra.core.services;

import io.kestra.core.models.flows.Flow;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface FlowListenersInterface {
    void run();

    void listen(Consumer<List<Flow>> consumer);

    void listen(BiConsumer<Flow, Flow> consumer);

    List<Flow> flows();
}
