package org.kestra.core.models.tasks;

import org.kestra.core.serializers.JacksonMapper;

import java.util.Map;

public interface Output {
    default Map<String, Object> toMap() {
        return JacksonMapper.toMap(this);
    }
}
