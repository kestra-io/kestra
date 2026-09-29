package io.kestra.executor.testkit;

import java.util.List;
import java.util.stream.Stream;

/**
 * What a closed-loop run did: each delivery the real executor received and what it emitted in
 * return, numbered across queues so ordering between queues can be asserted.
 */
public record Trace(List<Step> steps) {

    public record Emission(int sequence, String queue, Object message) {
        public <T> T as(Class<T> type) {
            return type.cast(message);
        }
    }

    public record Step(String queue, Object message, List<Emission> emitted) {
        public Stream<Emission> emitted(String queue) {
            return emitted.stream().filter(emission -> emission.queue().equals(queue));
        }
    }

    public Stream<Emission> emitted(String queue) {
        return steps.stream().flatMap(step -> step.emitted(queue));
    }

    public Stream<Step> deliveredFrom(String queue) {
        return steps.stream().filter(step -> step.queue().equals(queue));
    }
}
