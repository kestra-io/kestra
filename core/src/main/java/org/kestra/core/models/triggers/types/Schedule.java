package org.kestra.core.models.triggers.types;

import it.sauronsoftware.cron4j.Predictor;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.schedulers.validations.CronExpression;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Schedule extends Trigger implements PollingTriggerInterface {
    @NotNull
    @CronExpression
    private String cron;

    private ScheduleBackfill backfill;

    private Instant getNextScheduleDate() {
        return Instant.ofEpochMilli(new Predictor(cron).nextMatchingDate().getTime());
    }

    public Optional<Execution> evaluate(TriggerContext context) {
        return Optional.empty();
    }

    public boolean isReady(Instant now) {
        // Predictor returns next date when cron is on current Instant.
        // The date match is done on previous second.
        return getNextScheduleDate().getEpochSecond() - 1 == now.getEpochSecond();
    }

    public boolean hasNextSchedule() {
        return getNextScheduleDate().getEpochSecond() > Instant.now().getEpochSecond();
    }
}
