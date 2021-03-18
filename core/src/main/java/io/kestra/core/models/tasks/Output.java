package io.kestra.core.models.tasks;

import io.kestra.core.serializers.JacksonMapper;

import java.util.Map;

public interface Output {
    default Map<String, Object> toMap() {
        return JacksonMapper.toMap(this);
    }
}
