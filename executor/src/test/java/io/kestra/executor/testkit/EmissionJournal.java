package io.kestra.executor.testkit;

import java.util.ArrayList;
import java.util.List;

/**
 * One sequence shared by every recording queue of a harness, so what the executor emitted can be
 * read back in the order it happened <em>across</em> queues — the property the transactional-outbox
 * bugs were about, and one that per-queue lists cannot show.
 */
public final class EmissionJournal {

    /** One recorded emission: which queue, and the message as the queue received it. */
    public record Entry(int sequence, String queue, Object message) {
        public <T> boolean is(Class<T> type) {
            return type.isInstance(message);
        }

        public <T> T as(Class<T> type) {
            return type.cast(message);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public synchronized Entry record(String queue, Object message) {
        Entry entry = new Entry(entries.size(), queue, message);
        entries.add(entry);
        return entry;
    }

    public synchronized List<Entry> all() {
        return List.copyOf(entries);
    }

    /** Entries recorded at or after {@code fromSequence}. */
    public synchronized List<Entry> since(int fromSequence) {
        return List.copyOf(entries.subList(Math.min(fromSequence, entries.size()), entries.size()));
    }

    public synchronized int size() {
        return entries.size();
    }
}
