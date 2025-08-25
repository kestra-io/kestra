package io.kestra.core.reporter;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.kestra.core.models.ServerType;
import lombok.Builder;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Represents a Kestra Server Event.
 */
@Builder(toBuilder = true)
public record ServerEvent(
    String instanceUuid,
    String sessionUuid,
    ServerType serverType,
    String serverVersion,
    ZoneId zoneId,
    Object payload,
    String uuid,
    ZonedDateTime reportedAt
) {
    
    @JsonUnwrapped
    public Object payload() {
        return payload;
    }
    
}

