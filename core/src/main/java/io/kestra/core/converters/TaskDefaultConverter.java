package io.kestra.core.converters;

import io.kestra.core.models.flows.TaskDefault;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"rawtypes", "unchecked"})
@Prototype
public class TaskDefaultConverter implements TypeConverter<Map, TaskDefault> {
    @Override
    public Optional<TaskDefault> convert(Map map, Class<TaskDefault> targetType, ConversionContext context) {
        return Optional.of(TaskDefault.builder()
            .type((String) map.get("type"))
            .values(new HashMap<>((Map<String, Object>) map.get("values")))
            .forced((Boolean) map.getOrDefault("forced", false))
            .build()
        );
    }
}

