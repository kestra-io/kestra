package org.kestra.core.models.triggers.types;

import lombok.Value;

import java.time.Instant;

@Value
public class ScheduleBackfill {
    private Instant start;
}
