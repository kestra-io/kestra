package io.kestra.runner.kafka;


import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.schedulers.SchedulerTriggerStateInterface;

import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import javax.validation.ConstraintViolationException;

@Slf4j
@KafkaQueueEnabled
@Singleton
public class KafkaSchedulerTriggerState implements SchedulerTriggerStateInterface {
    private final ReadOnlyKeyValueStore<String, Trigger> store;
    private final QueueInterface<Trigger> triggerQueue;
    private final Map<String, Trigger> triggerLock;

    public KafkaSchedulerTriggerState(
        ReadOnlyKeyValueStore<String, Trigger> store,
        QueueInterface<Trigger> triggerQueue,
        Map<String, Trigger> triggerLock
    ) {
        this.store = store;
        this.triggerQueue = triggerQueue;
        this.triggerLock = triggerLock;
    }

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        return Optional
            .ofNullable(this.triggerLock.getOrDefault(trigger.uid(), null))
            .or(() -> Optional.ofNullable(this.store.get(trigger.uid())));
    }

    @Override
    public Trigger save(Trigger trigger) throws ConstraintViolationException {
        triggerQueue.emit(trigger);

        return trigger;
    }
}
