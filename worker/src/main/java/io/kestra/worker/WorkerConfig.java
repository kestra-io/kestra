package io.kestra.worker;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Worker configuration.
 *
 * @param pollingTriggerTimeout The maximum time a polling trigger evaluation is allowed to run on
 *        a worker before it is interrupted. This bounds evaluations that would otherwise block
 *        forever (e.g. a stuck {@code KafkaConsumer.poll()} during a consumer-group rebalance): on
 *        timeout the worker thread is freed and an error result is emitted so the scheduler releases
 *        the trigger evaluation lock instead of leaving the trigger silently stuck. Applies to
 *        polling triggers only (realtime triggers run for their whole lifetime by design). The
 *        default is deliberately generous so that legitimate long evaluations are never interrupted;
 *        lower it if faster recovery from hangs is desired.
 */
@ConfigurationProperties("kestra.worker")
public record WorkerConfig(
    @Bindable(defaultValue = "10m")
    @Nullable Duration pollingTriggerTimeout
) {
}
