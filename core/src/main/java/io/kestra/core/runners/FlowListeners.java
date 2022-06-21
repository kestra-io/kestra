package io.kestra.core.runners;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowListenersInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Slf4j
public class FlowListeners implements FlowListenersInterface {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<List<Flow>> TYPE_REFERENCE = new TypeReference<>(){};

    private final QueueInterface<Flow> flowQueue;

    private final List<Flow> flows;

    private final List<Consumer<List<Flow>>> consumers = new ArrayList<>();

    @Inject
    public FlowListeners(
        FlowRepositoryInterface flowRepository,
        @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<Flow> flowQueue
    ) {
        this.flowQueue = flowQueue;
        this.flows = flowRepository.findAll();
    }

    @Override
    public void run() {
        this.flowQueue.receive(flow -> {
            if (flow.isDeleted()) {
                this.remove(flow);
            } else {
                this.upsert(flow);
            }

            if (log.isTraceEnabled()) {
                log.trace("Received {} flow '{}.{}'",
                    flow.isDeleted() ? "deletion" : "update",
                    flow.getNamespace(),
                    flow.getId()
                );
            }

            this.notifyConsumers();
        });

        this.notifyConsumers();

        if (log.isTraceEnabled()) {
            log.trace("FlowListenersService started with {} flows", flows.size());
        }
    }

    private boolean remove(Flow flow) {
        synchronized (this) {
            boolean remove = flows.removeIf(r -> r.getNamespace().equals(flow.getNamespace()) && r.getId().equals(flow.getId()));
            if (!remove && flow.isDeleted()) {
                log.warn("Can't remove flow {}.{}", flow.getNamespace(), flow.getId());
            }

            return remove;
        }
    }

    private synchronized void upsert(Flow flow) {
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

    @Override
    public synchronized void listen(Consumer<List<Flow>> consumer) {
        synchronized (this) {
            consumers.add(consumer);
            consumer.accept(new ArrayList<>(this.flows()));
        }
    }

    @SneakyThrows
    @Override
    public List<Flow> flows() {
        // we forced a deep clone to avoid concurrency where instance are changed during iteration (especially scheduler).
        return MAPPER.readValue(MAPPER.writeValueAsString(this.flows), TYPE_REFERENCE);
    }
}
