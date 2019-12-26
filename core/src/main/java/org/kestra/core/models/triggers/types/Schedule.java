package org.kestra.core.models.triggers.types;

import lombok.*;
import org.kestra.core.models.triggers.Trigger;

@ToString
@EqualsAndHashCode
@Value
public class Schedule extends Trigger {
    private String expression;

    private ScheduleBackfill backfill;
}
