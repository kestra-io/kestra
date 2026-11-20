package io.kestra.worker;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.NotNull;

/**
 * Worker job stream configuration.
 * <p>
 * The worker syncs with the controller over its job stream: it reports the jobs it holds when it opens the stream and
 * then every {@code syncInterval}, the controller resubmits the jobs it sent on an earlier stream that the worker does
 * not report, and acknowledges each report. A stream whose reports stay unacknowledged for {@code syncTimeout} stopped
 * delivering, even when a proxy in between still answers the transport keepalive, so the worker drops it and reconnects.
 *
 * @param syncInterval how often the worker reports the jobs it holds.
 * @param syncTimeout how long the worker waits for a report to be acknowledged before it reconnects; keep it above
 *        {@code syncInterval}. It only applies to a stream whose controller has acknowledged a report, so that a
 *        controller that does not sync never causes a reconnection.
 */
@ConfigurationProperties("kestra.worker.job-stream")
public record WorkerJobStreamConfig(
    @Bindable(defaultValue = "30s") @NotNull Duration syncInterval,
    @Bindable(defaultValue = "65s") @NotNull Duration syncTimeout) {
}
