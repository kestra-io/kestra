package org.floworc.core.models.triggers.types;

import lombok.*;
import org.floworc.core.models.triggers.Trigger;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Value
public class Schedule extends Trigger {
    private String expression;

    private ScheduleBackfill backfill;
}
