package io.kestra.jdbc.runner;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * @param lateCommitRecheckInterval how often a broadcast subscriber looks for late commits, which is also about the extra delay such a message can take to be delivered
 * @param lateCommitWindow must exceed the longest transaction emitting a broadcast message and stay below the queue cleaner retention (not enforced)
 */
@ConfigurationProperties("kestra.jdbc.queues")
public record JdbcQueueConfiguration(
    @Bindable(defaultValue = "PT0.025S") Duration minPollInterval,
    @Bindable(defaultValue = "PT0.5S") Duration maxPollInterval,
    @Bindable(defaultValue = "PT60S") Duration pollSwitchInterval,
    @Bindable(defaultValue = "100") Integer pollSize,
    @Bindable(defaultValue = "5") Integer switchSteps,
    @Bindable(defaultValue = "true") Boolean immediateRepoll,
    @Bindable(defaultValue = "PT30S") Duration lateCommitWindow,
    @Bindable(defaultValue = "PT1S") Duration lateCommitRecheckInterval) {
}
