package io.kestra.repository.postgres;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the Postgres {@code LISTEN}/{@code NOTIFY} realtime wake-up mechanism (see
 * {@link PostgresQueueChangeNotifier}). Purely a latency optimization: the durable poll + ack
 * path delivering messages is unchanged and unaffected by these settings.
 */
@ConfigurationProperties("kestra.queue.postgres.notify")
public record PostgresQueueNotifyConfiguration(
    @Bindable(defaultValue = "true") Boolean enabled,
    // Matches kestra.jdbc.queues.min-poll-interval's own default: PostgresQueueChangeNotifier
    // floors the effective coalescing interval at that value regardless of what's configured
    // here, so this default just documents the resulting floor rather than being load-bearing.
    @Bindable(defaultValue = "PT0.025S") Duration coalesceInterval) {
}
