package io.kestra.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TriggerContext;

@AllArgsConstructor
@Getter
public class SchedulerExecutionWithTrigger {
    private final Execution execution;
    private final TriggerContext triggerContext;
}
