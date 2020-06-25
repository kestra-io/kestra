package org.kestra.core.models.triggers.types;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

@Value
@Builder
public class ScheduleBackfill {
    private ZonedDateTime start;
}
