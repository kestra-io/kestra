package io.kestra.core.models.triggers;

import io.kestra.core.models.executions.Execution;

import java.util.function.Consumer;

public interface PersistantTriggerInterface {
    void evaluate(TriggerContext context, Consumer<Execution> consumer);
}
