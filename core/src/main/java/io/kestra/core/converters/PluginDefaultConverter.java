package io.kestra.core.converters;

import io.kestra.core.models.flows.PluginDefault;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"rawtypes", "unchecked"})
@Prototype
public class PluginDefaultConverter implements TypeConverter<Map, PluginDefault> {
    @Override
    public Optional<PluginDefault> convert(Map map, Class<PluginDefault> targetType, ConversionContext context) {
        return Optional.of(PluginDefault.builder()
            .type((String) map.get("type"))
            .values(new HashMap<>((Map<String, Object>) map.get("values")))
            .forced((Boolean) map.getOrDefault("forced", false))
            .build()
        );
    }
}

