package org.kestra.core.services;

import lombok.Getter;
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
public class FlowListenersService {
    private final QueueInterface<Flow> flowQueue;

    @Getter
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

            this.notifyConsumers();
        });

        this.notifyConsumers();
    }

    private synchronized boolean remove(Flow flow) {
        return flows.removeIf(r -> r.equalsWithoutRevision(flow));
    }

    private synchronized void upsert(Flow flow) {
        this.remove(flow);

        this.flows.add(flow);
    }

    private void notifyConsumers() {
        this.consumers
            .forEach(consumer -> consumer.accept(this.flows));
    }

    public void listen(Consumer<List<Flow>> consumer) {
        consumers.add(consumer);
        consumer.accept(this.flows);
    }
}
