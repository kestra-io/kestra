package org.kestra.core.models.triggers;

import org.kestra.core.models.executions.Execution;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface PollingTriggerInterface {
    Optional<Execution> evaluate(TriggerContext context);

    default ZonedDateTime nextDate(Optional<? extends TriggerContext> last) {
        return ZonedDateTime.now(ZoneId.systemDefault());
    }
}
