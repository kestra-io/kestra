package io.kestra.scheduler;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TriggerContext;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class SchedulerExecutionWithTrigger {
    private final Execution execution;
    private final TriggerContext triggerContext;
}
