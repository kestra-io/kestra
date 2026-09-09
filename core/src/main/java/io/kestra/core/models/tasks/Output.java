package io.kestra.core.models.tasks;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;

public interface Output {
    default Optional<State.Type> finalState() {
        return Optional.empty();
    }

    /**
     * Nulls nested in the output are kept: an explicit <code>null</code> is data, not an absent key. Null
     * properties of the output itself are still omitted, unless annotated with
     * {@link com.fasterxml.jackson.annotation.JsonInclude}.
     */
    default Map<String, Object> toMap() {
        return JacksonMapper.toMapKeepingNullValues(this);
    }

    /** @see #toMap() */
    default Map<String, Object> toMap(ZoneId zoneId) {
        return JacksonMapper.toMapKeepingNullValues(this, zoneId);
    }
}
