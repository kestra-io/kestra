package io.kestra.core.tasks.test;

import org.reactivestreams.Publisher;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerEvaluationResult;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Sinks;

/**
 * This trigger is used in unit tests where we need a realtime trigger that keeps running on its worker until it is stopped.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class StoppableRealtimeTrigger extends AbstractTrigger implements RealtimeTriggerInterface {

    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final transient Sinks.Empty<TriggerEvaluationResult> stopSignal = Sinks.empty();

    @Override
    public Publisher<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) {
        return stopSignal.asMono();
    }

    @Override
    public void stop() {
        stopSignal.tryEmitEmpty();
    }
}
