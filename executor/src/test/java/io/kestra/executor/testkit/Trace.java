package io.kestra.executor.testkit;

import java.util.List;
import java.util.stream.Stream;

/**
 * The journal of a closed-loop run: each delivery the real executor received (which queue, which
 * message) and everything it emitted in return, as {@link EmissionJournal.Entry}s so order is kept
 * across queues.
 */
public record Trace(List<Step> steps) {

    public record Step(String queue, Object message, List<EmissionJournal.Entry> emitted) {
        public Stream<EmissionJournal.Entry> emitted(String queue) {
            return emitted.stream().filter(entry -> entry.queue().equals(queue));
        }
    }

    public Stream<EmissionJournal.Entry> emitted() {
        return steps.stream().flatMap(step -> step.emitted().stream());
    }

    public Stream<EmissionJournal.Entry> emitted(String queue) {
        return emitted().filter(entry -> entry.queue().equals(queue));
    }

    public Stream<Step> deliveredFrom(String queue) {
        return steps.stream().filter(step -> step.queue().equals(queue));
    }
}
