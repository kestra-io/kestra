package io.kestra.core.runners;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.services.PluginDefaultService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowListenersInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Slf4j
public class FlowListeners implements FlowListenersInterface {

    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final QueueInterface<FlowInterface> flowQueue;
    private final List<FlowWithSource> flows;
    private final List<Consumer<List<FlowWithSource>>> consumers = new ArrayList<>();
    private final List<BiConsumer<FlowWithSource, FlowWithSource>> consumersEach = new ArrayList<>();

    private final PluginDefaultService pluginDefaultService;

    @Inject
    public FlowListeners(
        FlowRepositoryInterface flowRepository,
        @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<FlowInterface> flowQueue,
        PluginDefaultService pluginDefaultService
    ) {
        this.flowQueue = flowQueue;
        this.flows = new ArrayList<>(flowRepository.findAllWithSourceForAllTenants());
        this.pluginDefaultService = pluginDefaultService;
    }

    @Override
    public void run() {
        synchronized (this) {
            if (this.isStarted.compareAndSet(false, true)) {
                this.flowQueue.receive(either -> {
                    FlowWithSource flow;
                    if (either.isRight()) {
                        flow = FlowWithException.from(either.getRight().getRecord(), either.getRight(), log).orElse(null);
                    } else {
                        try {
                            flow = pluginDefaultService.injectVersionDefaults(either.getLeft(), true);
                        } catch (FlowProcessingException ignore) {
                            // should not occur, safe = true...
                            flow = null;
                        }
                    }

                    if (flow == null) {
                        return;
                    }

                    final FlowWithSource previous = this.previous(flow).orElse(null);

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

                    this.notifyConsumersEach(flow, previous);
                    this.notifyConsumers();
                });

                if (log.isTraceEnabled()) {
                    log.trace("FlowListenersService started with {} flows", flows.size());
                }
            }

            this.notifyConsumers();
        }
    }

    private Optional<FlowWithSource> previous(final FlowWithSource flow) {
        List<FlowWithSource> copy = new ArrayList<>(flows);
        return copy.stream().filter(r -> r.isSameId(flow)).findFirst();
    }

    private boolean remove(FlowInterface flow) {
        synchronized (this) {
            boolean remove = flows.removeIf(r -> r.isSameId(flow));
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
            this.consumers.forEach(consumer -> consumer.accept(new ArrayList<>(this.flows)));
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
