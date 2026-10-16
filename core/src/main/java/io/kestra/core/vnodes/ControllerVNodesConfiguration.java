package io.kestra.core.vnodes;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * The vNodes the worker controllers share, to which the jobs targeting one worker are sent.
 *
 * @param count the number of vNodes.
 * @param rebalanceTimeout the maximum duration to wait for the controllers to reply to a rebalance.
 */
@ConfigurationProperties("kestra.controller.vnodes")
public record ControllerVNodesConfiguration(
    @Bindable(defaultValue = "32") Integer count,
    @Bindable(defaultValue = "PT5S") Duration rebalanceTimeout) {
}
