package io.kestra.core.worker.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.scheduler.model.TriggerType;

import jakarta.annotation.Nullable;

/**
 * Represents the result of a trigger evaluation by a worker.
 *
 * @param id the trigger id.
 * @param type the trigger type.
 * @param evaluation the lightweight trigger evaluation result, or {@code null} if the trigger did not match.
 */
public record WorkerTriggerResult(
    @JsonProperty
    @JsonDeserialize(as = TriggerId.Default.class) TriggerId id,
    @JsonProperty TriggerType type,
    @JsonProperty @Nullable TriggerEvaluationResult evaluation) {

    /**
     * Create a new {@link WorkerTriggerResult} from a {@link WorkerTrigger} and a {@link TriggerEvaluationResult}.
     *
     * @param trigger    the trigger.
     * @param evaluation the evaluation result, or {@code null} if the trigger did not match.
     * @return a new {@link WorkerTriggerResult}.
     */
    public static WorkerTriggerResult of(WorkerTrigger trigger, @Nullable TriggerEvaluationResult evaluation) {
        return new WorkerTriggerResult(
            trigger.triggerId(),
            TriggerType.from(trigger.getTrigger()),
            evaluation
        );
    }
}
