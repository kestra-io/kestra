package io.kestra.executor.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.executor")
public record ExecutorConfiguration(
    @Bindable(defaultValue = "0") Integer threadCount,
    @Bindable(defaultValue = "1000") Integer executionDelayLoopPeriodicityMs,
    @Bindable(defaultValue = "1000") Integer monitorSLALoopPeriodicityMs,
    @Bindable(defaultValue = "60000") Integer multipleConditionPurgeLoopPeriodicityMs) {
}
