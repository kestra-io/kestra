package io.kestra.jdbc.runner;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.jdbc.queues")
public record JdbcQueueConfiguration(
    @Bindable(defaultValue = "PT0.025S") Duration minPollInterval,
    @Bindable(defaultValue = "PT0.5S") Duration maxPollInterval,
    @Bindable(defaultValue = "PT60S") Duration pollSwitchInterval,
    @Bindable(defaultValue = "100") Integer pollSize,
    @Bindable(defaultValue = "5") Integer switchSteps,
    @Bindable(defaultValue = "true") Boolean immediateRepoll,
    @Bindable(defaultValue = "PT30S") Duration broadcastGapTimeout) {

    public JdbcQueueConfiguration(
        Duration minPollInterval,
        Duration maxPollInterval,
        Duration pollSwitchInterval,
        Integer pollSize,
        Integer switchSteps,
        Boolean immediateRepoll) {
        this(minPollInterval, maxPollInterval, pollSwitchInterval, pollSize, switchSteps, immediateRepoll, Duration.ofSeconds(30));
    }

    @Override
    public Duration broadcastGapTimeout() {
        return broadcastGapTimeout != null ? broadcastGapTimeout : Duration.ofSeconds(30);
    }
}
