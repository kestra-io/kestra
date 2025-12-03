package io.kestra.scheduler.pubsub;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.models.triggers.WorkerTriggerInterface;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.scheduler.TriggerEventQueue;
import io.kestra.core.scheduler.events.TriggerCreated;
import io.kestra.core.scheduler.events.TriggerDeleted;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerUpdated;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
@Requires(property = "kestra.server-type", pattern = "(SCHEDULER|STANDALONE)")
@Slf4j
public class TriggerEventPublisher implements AutoCloseable {

    private final QueueInterface<FlowInterface> flowQueue;
    private final TriggerEventQueue triggerEventQueue;
    private final FlowRepositoryInterface flowRepository;
    private final PluginDefaultService pluginDefaultService;

    private Disposable cancellation;

    @Inject
    public TriggerEventPublisher(@Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<FlowInterface> flowQueue,
                                 TriggerEventQueue triggerEventQueue,
                                 FlowRepositoryInterface flowRepository,
                                 PluginDefaultService pluginDefaultService) {
        this.flowQueue = flowQueue;
        this.triggerEventQueue = triggerEventQueue;
        this.flowRepository = flowRepository;
        this.pluginDefaultService = pluginDefaultService;

    }

    // Make it a StartupEvent listener so it starts when Kestra start
    @EventListener
    public void run(StartupEvent event) {
        this.cancellation = Disposable.of(this.flowQueue.receive(Scheduler.class, either -> {
            if (either.isRight()) {
                log.error("Unable to deserialize a flow: {}", either.getRight().getMessage());
                return;
            }

            FlowWithSource flow;
            try {
                flow = pluginDefaultService.injectVersionDefaults(either.getLeft(), true);
            } catch (FlowProcessingException e) {
                log.error("Unable to inject version defaults for flow {}", either.getLeft().getId(), e);
                return;
            }

            var previous = flow.getRevision() <= 1 ? null : flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(flow.getRevision() - 1)).orElse(null);

            if (flow.isDeleted() || previous != null) {
                List<AbstractTrigger> triggersDeleted = flow.isDeleted() ?
                    ListUtils.emptyOnNull(flow.getTriggers()) :
                    FlowService.findRemovedTrigger(flow, previous);

                triggersDeleted.forEach(trigger ->
                    sendEvent(new TriggerDeleted(TriggerId.of(flow, trigger)))
                );
            }

            if (previous != null && !Objects.equals(previous.getRevision(), flow.getRevision())) {
                FlowService.findUpdatedTrigger(flow, previous)
                    .stream()
                    .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                    .forEach(trigger ->
                        sendEvent(new TriggerUpdated(TriggerId.of(flow, trigger), flow.getRevision()))
                    );
                return;
            }

            if (flow.getTriggers() != null) {
                flow.getTriggers()
                    .stream()
                    .filter(trigger -> trigger instanceof WorkerTriggerInterface)
                    .forEach(trigger ->
                        sendEvent(new TriggerCreated(TriggerId.of(flow, trigger), flow.getRevision()))
                    );
            }
        }));
    }

    @PreDestroy
    @Override
    public void close() {
        this.cancellation.dispose();
    }

    private void sendEvent(TriggerEvent event) {
        this.triggerEventQueue.send(event);
    }
}
