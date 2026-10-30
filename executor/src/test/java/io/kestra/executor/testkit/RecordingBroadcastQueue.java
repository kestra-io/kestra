package io.kestra.executor.testkit;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.event.BroadcastEvent;

public class RecordingBroadcastQueue<T extends BroadcastEvent> extends RecordingQueue<T> implements BroadcastQueueInterface<T> {
    public RecordingBroadcastQueue(String name) {
        this(name, new ArrayList<>());
    }

    public RecordingBroadcastQueue(String name, List<Trace.Emission> journal) {
        super(name, journal);
    }
}
