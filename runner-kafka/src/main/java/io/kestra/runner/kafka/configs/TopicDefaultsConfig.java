package io.kestra.runner.kafka.configs;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import lombok.Getter;

import java.util.Map;

@ConfigurationProperties("kestra.kafka.defaults.topic")
@Getter
public class TopicDefaultsConfig {
    int partitions = 6;

    short replicationFactor = 1;

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    Map<String, String> properties;
}

