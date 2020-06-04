package org.kestra.core.models.triggers;

import org.kestra.core.models.executions.Execution;

import java.util.Optional;

public interface PollingTriggerInterface {
    Optional<Execution> evaluate(TriggerContext context);
}
