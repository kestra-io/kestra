package io.kestra.worker;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.Min;

/**
 * Worker configuration.
 *
 * @param pollingTriggerTimeout How long the worker waits for a polling trigger evaluation before it
 *        gives up on it. This bounds evaluations that would otherwise block forever (e.g. a stuck
 *        {@code KafkaConsumer.poll()} during a consumer-group rebalance): at the deadline an error
 *        result is emitted so the scheduler releases the trigger evaluation lock, instead of leaving the
 *        trigger silently stuck. The evaluation itself keeps its worker thread until the plugin returns,
 *        and the worker waits for that thread rather than taking new work on it, so it runs one thread
 *        short until then. Only the plugin's own {@code kill()} can cut that short. Applies to polling triggers only
 *        (realtime triggers run for their whole lifetime by design). The default is deliberately
 *        generous so that legitimate long evaluations are never cut short; lower it if faster
 *        recovery from hangs is desired.
 * @param jobBufferSize How many fetched jobs the worker holds while all its threads are busy, defaulting to
 *        the thread count, which is also its maximum. Lowering it leaves more of the backlog on the controller,
 *        where newly started workers can pick it up instead of it waiting on a saturated worker, at the cost of
 *        dispatch throughput for short tasks. {@code 0} only requests a job when a thread is idle.
 */
@ConfigurationProperties("kestra.worker")
public record WorkerConfig(
    @Bindable(defaultValue = "10m")
    @Nullable Duration pollingTriggerTimeout,

    @Nullable @Min(0) Integer jobBufferSize) {
}
