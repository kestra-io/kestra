package org.kestra.core.models.triggers;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(
        title = "Interval between polling",
        description = "The interval between 2 different test of schedule, this can avoid to overload the remote system " +
            "with too many call. For most of trigger that depend on external system, a minimal interval must be " +
            "at least PT30S.",
        defaultValue = "PT1S"
    )
    Duration getInterval();
}
