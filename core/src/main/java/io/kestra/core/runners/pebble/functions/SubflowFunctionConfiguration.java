package io.kestra.core.runners.pebble.functions;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the {@code subflow()} Pebble function.
 *
 * @param completionTimeout maximum time to wait for the synchronously-run subflow to terminate.
 *        Kept short by default because the call blocks input rendering (e.g. the execute form).
 * @param maxDepth maximum nesting depth of {@code subflow()} calls on the same rendering thread,
 *        guarding against runaway recursion (a subflow whose own inputs call {@code subflow()}).
 */
@ConfigurationProperties("kestra.pebble.subflow-function")
public record SubflowFunctionConfiguration(
    @Bindable(defaultValue = "PT1M") Duration completionTimeout,
    @Bindable(defaultValue = "3") int maxDepth
) {
}
