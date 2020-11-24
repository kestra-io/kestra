package org.kestra.core.services;

import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class FlowListenersService implements FlowListenersInterface {
    private final QueueInterface<Flow> flowQueue;

    private final List<Flow> flows;

    private final List<Consumer<List<Flow>>> consumers = new ArrayList<>();

    @Inject
    public FlowListenersService(
        FlowRepositoryInterface flowRepository,
        @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<Flow> flowQueue
    ) {
        this.flowQueue = flowQueue;

        this.flows = flowRepository.findAll();

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
        this.consumers
            .forEach(consumer -> consumer.accept(new ArrayList<>(this.flows)));
    }

    @Override
    public void listen(Consumer<List<Flow>> consumer) {
        consumers.add(consumer);
        consumer.accept(new ArrayList<>(this.flows));
    }

    @Override
    public List<Flow> flows() {
        return this.flows;
    }
}
