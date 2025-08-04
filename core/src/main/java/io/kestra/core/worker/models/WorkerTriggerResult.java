package io.kestra.core.worker.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.scheduler.model.TriggerType;


/**
 * Represents the result of a trigger evaluation by a worker.
 *
 * @param id        the trigger id
 * @param type      the trigger type.
 * @param execution the resulting execution.
 */
public record WorkerTriggerResult(
    @JsonProperty
    @JsonDeserialize(as = TriggerId.Default.class)
    TriggerId id,
    @JsonProperty
    TriggerType type,
    @JsonProperty
    Execution execution
) {

    /**
     * Create a new {@link WorkerTriggerResult} from a {@link WorkerTrigger} and an {@link Execution}.
     *
     * @param trigger   the trigger
     * @param execution the resulting execution
     * @return a new {@link WorkerTriggerResult}
     */
    public static WorkerTriggerResult of(WorkerTrigger trigger, Execution execution) {
        return new WorkerTriggerResult(
            TriggerId.of(trigger.getTriggerContext()),
            TriggerType.from(trigger.getTrigger()),
            execution
        );
    }
}
