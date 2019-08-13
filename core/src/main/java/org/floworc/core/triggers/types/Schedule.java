package org.floworc.core.triggers.types;

import lombok.*;
import org.floworc.core.triggers.Trigger;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class Schedule extends Trigger {
    private String expression;

    private ScheduleBackfill backfill;
}
