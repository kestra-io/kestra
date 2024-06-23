package io.kestra.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.context.annotation.Requires;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Requires(beans = MetricConfig.class)
public class GlobalTagsConfigurer implements MeterRegistryConfigurer<SimpleMeterRegistry> {
    @Inject
    MetricConfig metricConfig;

    @Override
    public void configure(SimpleMeterRegistry meterRegistry) {
        if (metricConfig.getTags() != null) {
            meterRegistry
                .config()
                .commonTags(
                    metricConfig.getTags()
                        .entrySet()
                        .stream()
                        .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                        .toList()
                        .toArray(String[]::new)
                );
        }
    }

    @Override
    public boolean supports(SimpleMeterRegistry meterRegistry) {
        return true;
    }

    @Override
    public Class<SimpleMeterRegistry> getType() {
        return SimpleMeterRegistry.class;
    }

}