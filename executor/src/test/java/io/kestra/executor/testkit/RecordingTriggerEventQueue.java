package io.kestra.executor.testkit;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.utils.Disposable;

/**
 * Records the {@link TriggerEvent}s the executor sends to the scheduler (trigger terminated).
 */
public class RecordingTriggerEventQueue implements TriggerEventQueue {
    public static final String NAME = "triggerEvent";

    private final List<TriggerEvent> sent = new CopyOnWriteArrayList<>();
    private final EmissionJournal journal;

    public RecordingTriggerEventQueue(EmissionJournal journal) {
        this.journal = journal;
    }

    @Override
    public void send(TriggerEvent triggerEvent) {
        sent.add(triggerEvent);
        journal.record(NAME, triggerEvent);
    }

    @Override
    public Disposable subscribe(Set<Integer> vNodes, BatchRecordConsumer consumer) {
        throw new UnsupportedOperationException("RecordingTriggerEventQueue does not support subscriptions");
    }

    @Override
    public void close() {
        // nothing to close
    }

    public List<TriggerEvent> sent() {
        return List.copyOf(sent);
    }
}
