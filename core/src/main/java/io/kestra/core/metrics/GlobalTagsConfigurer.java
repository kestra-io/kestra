package io.kestra.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.context.annotation.Requires;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("rawtypes")
@Singleton
@Requires(beans = MetricConfig.class)
public class GlobalTagsConfigurer implements MeterRegistryConfigurer {
    @Inject
    MetricConfig metricConfig;

    @Override
    public void configure(MeterRegistry meterRegistry) {
        if (metricConfig.getTags() != null) {
            meterRegistry
                .config()
                .commonTags(
                    metricConfig.getTags()
                        .entrySet()
                        .stream()
                        .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                        .collect(Collectors.toList())
                        .toArray(String[]::new)
                );
        }
    }

    @Override
    public boolean supports(MeterRegistry meterRegistry) {
        return true;
    }
}