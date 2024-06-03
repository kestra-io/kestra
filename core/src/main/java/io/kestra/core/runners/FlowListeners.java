package io.kestra.core.runners;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.JacksonMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowListenersInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Slf4j
public class FlowListeners implements FlowListenersInterface {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private Boolean isStarted = false;
    private final QueueInterface<FlowWithSource> flowQueue;
    private final List<FlowWithSource> flows;
    private final List<Consumer<List<FlowWithSource>>> consumers = new ArrayList<>();

    private final List<BiConsumer<FlowWithSource, FlowWithSource>> consumersEach = new ArrayList<>();

    @Inject
    public FlowListeners(
        FlowRepositoryInterface flowRepository,
        @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<FlowWithSource> flowQueue
    ) {
        this.flowQueue = flowQueue;
        this.flows = flowRepository.findAllWithSourceForAllTenants();
    }

    @Override
    public void run() {
        synchronized (this) {
            if (!this.isStarted) {
                this.isStarted = true;

                this.flowQueue.receive(either -> {
                    FlowWithSource flow;
                    if (either.isRight()) {
                        log.error("Unable to deserialize a flow: {}", either.getRight().getMessage());
                        try {
                            var jsonNode = MAPPER.readTree(either.getRight().getRecord());
                            flow = FlowWithException.from(jsonNode, either.getRight()).orElseThrow(IOException::new);
                        } catch (IOException e) {
                            // if we cannot create a FlowWithException, ignore the message
                            log.error("Unexpected exception when trying to handle a deserialization error", e);
                            return;
                        }
                    }
                    else {
                        flow = either.getLeft();
                    }
                    Optional<FlowWithSource> previous = this.previous(flow);

                    if (flow.isDeleted()) {
                        this.remove(flow);
                    } else {
                        this.upsert(flow);
                    }

                    if (log.isTraceEnabled()) {
                        log.trace(
                            "Received {} flow '{}.{}'",
                            flow.isDeleted() ? "deletion" : "update",
                            flow.getNamespace(),
                            flow.getId()
                        );
                    }

                    this.notifyConsumersEach(flow, previous.orElse(null));
                    this.notifyConsumers();
                });

                if (log.isTraceEnabled()) {
                    log.trace("FlowListenersService started with {} flows", flows.size());
                }
            }

            this.notifyConsumers();
        }
    }

    private Optional<FlowWithSource> previous(FlowWithSource flow) {
        return flows
            .stream()
            .filter(r -> Objects.equals(r.getTenantId(), flow.getTenantId()) && r.getNamespace().equals(flow.getNamespace()) && r.getId().equals(flow.getId()))
            .findFirst();
    }

    private boolean remove(FlowWithSource flow) {
        synchronized (this) {
            boolean remove = flows.removeIf(r -> Objects.equals(r.getTenantId(), flow.getTenantId()) && r.getNamespace().equals(flow.getNamespace()) && r.getId().equals(flow.getId()));
            if (!remove && flow.isDeleted()) {
                log.warn("Can't remove flow {}.{}", flow.getNamespace(), flow.getId());
            }

            return remove;
        }
    }

    private void upsert(FlowWithSource flow) {
        synchronized (this) {
            this.remove(flow);

            this.flows.add(flow);
        }
    }

    private void notifyConsumers() {
        synchronized (this) {
            this.consumers
                .forEach(consumer -> consumer.accept(new ArrayList<>(this.flows)));
        }
    }

    private void notifyConsumersEach(FlowWithSource flow, FlowWithSource previous) {
        synchronized (this) {
            this.consumersEach
                .forEach(consumer -> consumer.accept(flow, previous));
        }
    }

    @Override
    public void listen(Consumer<List<FlowWithSource>> consumer) {
        synchronized (this) {
            consumers.add(consumer);
            consumer.accept(new ArrayList<>(this.flows()));
        }
    }

    @Override
    public void listen(BiConsumer<FlowWithSource, FlowWithSource> consumer) {
        synchronized (this) {
            consumersEach.add(consumer);
        }
    }

    @SneakyThrows
    @Override
    public List<FlowWithSource> flows() {
        // we forced a deep clone to avoid concurrency where instance are changed during iteration (especially scheduler).
        return new ArrayList<>(this.flows);
    }
}
