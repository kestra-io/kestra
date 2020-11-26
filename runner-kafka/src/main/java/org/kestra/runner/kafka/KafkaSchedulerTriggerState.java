package org.kestra.runner.kafka;


import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.schedulers.SchedulerTriggerStateInterface;

import java.util.Optional;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;

@Slf4j
@KafkaQueueEnabled
@Singleton
public class KafkaSchedulerTriggerState implements SchedulerTriggerStateInterface {
    private final ReadOnlyKeyValueStore<String, Trigger> store;
    private final QueueInterface<Trigger> triggerQueue;

    public KafkaSchedulerTriggerState(
        ReadOnlyKeyValueStore<String, Trigger> store,
        QueueInterface<Trigger> triggerQueue
    ) {
        this.store = store;
        this.triggerQueue = triggerQueue;
    }

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        return Optional.ofNullable(this.store.get(trigger.uid()));
    }

    @Override
    public Trigger save(Trigger trigger) throws ConstraintViolationException {
        triggerQueue.emit(trigger);

        return trigger;
    }
}
