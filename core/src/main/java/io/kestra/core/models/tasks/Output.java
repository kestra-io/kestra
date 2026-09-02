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
     * Convert this output to a map, as exposed to expressions and persisted by the output services.
     * <p>
     * Null map entries and collection elements are kept: for an output holding arbitrary data, an explicit
     * <code>null</code> is a value and must not be turned into an absent key. Null properties of the output
     * itself are still omitted, unless annotated with {@link com.fasterxml.jackson.annotation.JsonInclude}.
     */
    default Map<String, Object> toMap() {
        return JacksonMapper.toMapKeepingNullValues(this);
    }

    /**
     * @see #toMap()
     */
    default Map<String, Object> toMap(ZoneId zoneId) {
        return JacksonMapper.toMapKeepingNullValues(this, zoneId);
    }
}
