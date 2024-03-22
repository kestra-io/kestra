package io.kestra.core.models.triggers;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface RealtimeTriggerInterface extends WorkerTriggerInterface {
    Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception;
}
