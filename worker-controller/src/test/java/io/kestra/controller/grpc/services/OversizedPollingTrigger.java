package io.kestra.controller.grpc.services;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerService;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
public class OversizedPollingTrigger extends AbstractTrigger implements PollingTriggerInterface {
    private static final AtomicInteger POLLS = new AtomicInteger();

    static void reset() {
        POLLS.set(0);
    }

    static int polls() {
        return POLLS.get();
    }

    @Override
    public Optional<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) {
        int poll = POLLS.incrementAndGet();
        Map<String, Object> variables = poll == 1
            ? Map.of("payload", "x".repeat(1_500_000))
            : Map.of("payload", "small");
        return Optional.of(TriggerService.generateEvaluationResult(this, conditionContext, variables));
    }

    @Override
    public Duration getInterval() {
        return Duration.ofSeconds(3);
    }
}
