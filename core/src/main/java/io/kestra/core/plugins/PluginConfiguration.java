package io.kestra.core.plugins;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.convert.format.MapFormat;
import jakarta.validation.constraints.NotNull;

import java.util.Comparator;
import java.util.Map;

@EachProperty(value = "kestra.plugins.configurations", list = true)
public record PluginConfiguration(@Parameter Integer order,
                                  @NotNull String type,
                                  @MapFormat(transformation = MapFormat.MapTransformation.FLAT) Map<String, Object> values) {

    static final Comparator<PluginConfiguration> COMPARATOR = Comparator.comparing(PluginConfiguration::order);
}
