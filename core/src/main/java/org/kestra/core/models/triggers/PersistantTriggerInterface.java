package org.kestra.core.models.triggers;

import org.kestra.core.models.executions.Execution;

import java.util.Optional;
import java.util.function.Consumer;

public interface PersistantTriggerInterface {
    void evaluate(TriggerContext context, Consumer<Execution> consumer);
}
