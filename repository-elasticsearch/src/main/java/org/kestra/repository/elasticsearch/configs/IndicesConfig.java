package org.kestra.repository.elasticsearch.configs;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.convert.format.MapFormat;
import lombok.Getter;

import java.util.Map;

@EachProperty("kestra.elasticsearch.indices")
@Getter
public class IndicesConfig {
    String cls;

    String name;

    String settings;

    String mapping;

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    Map<String, String> properties;

    public IndicesConfig(@Parameter String cls) {
        this.cls = cls;
    }
}

