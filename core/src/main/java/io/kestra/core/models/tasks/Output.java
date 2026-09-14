package io.kestra.core.models.tasks;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.flows.State;
import io.kestra.core.serializers.JacksonMapper;

public interface Output {
    TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

    default Optional<State.Type> finalState() {
        return Optional.empty();
    }

    /**
     * Nulls nested in the output are kept: an explicit <code>null</code> is data, not an absent key. Null
     * properties of the output itself are still omitted, unless annotated with
     * {@link com.fasterxml.jackson.annotation.JsonInclude}.
     */
    default Map<String, Object> toMap() {
        return JacksonMapper.ofJsonWithNullValues().convertValue(this, MAP_TYPE_REFERENCE);
    }

    /** @see #toMap() */
    default Map<String, Object> toMap(ZoneId zoneId) {
        return JacksonMapper.ofJsonWithNullValues()
            .copy()
            .setTimeZone(TimeZone.getTimeZone(zoneId.getId()))
            .convertValue(this, MAP_TYPE_REFERENCE);
    }
}
