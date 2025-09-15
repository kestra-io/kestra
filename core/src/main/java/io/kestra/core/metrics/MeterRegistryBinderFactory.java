package io.kestra.core.metrics;

import io.micrometer.core.instrument.binder.jvm.JvmThreadDeadlockMetrics;
import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;
import static io.micronaut.core.util.StringUtils.FALSE;

@Factory
@RequiresMetrics

public class MeterRegistryBinderFactory {
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".jvm.enabled", notEquals = FALSE)
    public VirtualThreadMetrics virtualThreadMetrics() {
        return new VirtualThreadMetrics();
    }

    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_BINDERS + ".jvm.enabled", notEquals = FALSE)
    public JvmThreadDeadlockMetrics threadDeadlockMetricsMetrics() {
        return new JvmThreadDeadlockMetrics();
    }
}
