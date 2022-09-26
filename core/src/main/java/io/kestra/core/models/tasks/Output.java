package io.kestra.core.models.tasks;

import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

public interface Output {
    default Optional<State.Type> finalState() {
        return Optional.empty();
    }

    default Map<String, Object> toMap() {
        return JacksonMapper.toMap(this);
    }

    default Map<String, Object> toMap(ZoneId zoneId) {
        return JacksonMapper.toMap(this, zoneId);
    }
}
