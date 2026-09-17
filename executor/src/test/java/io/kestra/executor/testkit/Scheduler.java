package io.kestra.executor.testkit;

import java.util.List;
import java.util.Random;

/**
 * Decides which subscribed queue's oldest message the executor receives next. Within a queue
 * delivery is always FIFO, as in production; the scheduler only chooses between queues — exactly
 * the freedom production has, and no more.
 */
@FunctionalInterface
public interface Scheduler {
    Deliverable pick(List<Deliverable> ready);

    /** The first non-empty queue in subscription order — a readable, stable schedule. */
    static Scheduler fifo() {
        return List::getFirst;
    }

    /** Any non-empty queue; reproducible from the seed, so a failing interleaving can be replayed. */
    static Scheduler random(long seed) {
        Random random = new Random(seed);
        return ready -> ready.get(random.nextInt(ready.size()));
    }
}
