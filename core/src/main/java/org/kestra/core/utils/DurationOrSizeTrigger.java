package org.kestra.core.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Predicate;

public class DurationOrSizeTrigger<V> implements Predicate<Collection<V>> {
    private final int batchSize;
    private final Duration batchDuration;
    private Instant next;

    public DurationOrSizeTrigger(Duration batchDuration, int batchSize) {
        this.batchDuration = batchDuration;
        this.batchSize = batchSize;
        this.next = Instant.now().plus(batchDuration);
    }

    Instant getNext() {
        return next;
    }

    @Override
    public boolean test(Collection<V> buffer) {
        if (buffer.size() >= this.batchSize) {
            this.nextDate();
            return true;
        }

        if (buffer.size() > 0 && this.next.isBefore(Instant.now())) {
            this.nextDate();
            return true;
        }

        return false;
    }

    private void nextDate() {
        while (this.next.isBefore(Instant.now())) {
            this.next = this.next.plus(batchDuration);
        }
    }
}
