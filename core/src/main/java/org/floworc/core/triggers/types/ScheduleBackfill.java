package org.floworc.core.triggers.types;

import lombok.*;

import java.time.Instant;

@Data
public class ScheduleBackfill {
    private Instant start;
}
