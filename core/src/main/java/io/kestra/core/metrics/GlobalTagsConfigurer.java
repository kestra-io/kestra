package io.kestra.core.metrics;

import io.kestra.core.models.ServerType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.context.annotation.Requires;

import java.util.stream.Stream;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Requires(beans = MetricConfig.class)
public class GlobalTagsConfigurer implements MeterRegistryConfigurer<SimpleMeterRegistry> {
    @Inject
    MetricConfig metricConfig;

    @Nullable
    @Value("${kestra.server-type}")
    ServerType serverType;

    @Override
    public void configure(SimpleMeterRegistry meterRegistry) {
        String[] tags = Stream
            .concat(
                metricConfig.getTags() != null ? metricConfig.getTags()
                    .entrySet()
                    .stream()
                    .flatMap(e -> Stream.of(e.getKey(), e.getValue())) : Stream.empty(),
                serverType != null ? Stream.of("server_type", serverType.name()) : Stream.empty()
            )
            .toList()
            .toArray(String[]::new);

        meterRegistry
            .config()
            .commonTags(tags);
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