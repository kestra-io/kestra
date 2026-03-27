package io.kestra.core.services;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.kestra.core.models.flows.FlowWithSource;

public interface FlowListenersInterface {
    void run();

    void listen(Consumer<List<FlowWithSource>> consumer);

    void listen(BiConsumer<FlowWithSource, FlowWithSource> consumer);

    List<FlowWithSource> flows();
}
