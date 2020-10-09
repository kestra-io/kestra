package org.kestra.core.schedulers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.triggers.TriggerContext;

@AllArgsConstructor
@Getter
class SchedulerExecutionWithTrigger {
    private final Execution execution;
    private final TriggerContext triggerContext;
}
