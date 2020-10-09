package org.kestra.core.models.triggers;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.runners.RunContext;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface PollingTriggerInterface {
    Optional<Execution> evaluate(RunContext runContext, TriggerContext context) throws Exception;

    default ZonedDateTime nextDate(Optional<? extends TriggerContext> last) {
        return ZonedDateTime.now();
    }

    Duration getInterval();
}
