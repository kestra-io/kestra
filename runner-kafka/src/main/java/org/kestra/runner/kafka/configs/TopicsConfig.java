package org.kestra.runner.kafka.configs;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.convert.format.MapFormat;
import lombok.Getter;

import java.util.Map;

@EachProperty("kestra.kafka.topics")
@Getter
public class TopicsConfig {
    String key;

    Class<?> cls;

    String name;

    Integer partitions;

    Short replicationFactor;

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    Map<String, String> properties;

    public TopicsConfig(@Parameter String key) {
        this.key = key;
    }
}

