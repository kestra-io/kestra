package org.kestra.core.models.triggers.types;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import it.sauronsoftware.cron4j.Predictor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.schedulers.validations.CronExpression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import javax.validation.constraints.NotNull;

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

    public Optional<Execution> evaluate(TriggerContext context) {
        ZonedDateTime next = zonedDateTime(new Predictor(cron).nextMatchingDate().getTime());

        // Predictor returns next date when cron is on current Instant.
        // The date match is done on previous second.
        boolean isReady = next.toEpochSecond() - 1 == context.getDate().toEpochSecond();

        if (!isReady) {
            return Optional.empty();
        }

        Execution execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace(context.getFlow().getNamespace())
            .flowId(context.getFlow().getId())
            .flowRevision(context.getFlow().getRevision())
            .state(new State())
            .variables(ImmutableMap.of(
                "schedule", ImmutableMap.of(
                    "date", next,
                    "next", zonedDateTime(new Predictor(cron, next.toInstant().toEpochMilli()).nextMatchingDate().getTime())
                )
            ))
            .build();

        return Optional.of(execution);
    }

    private static ZonedDateTime zonedDateTime(long millis) {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault());
    }

    @Override
    public String toLog() {
        return this.cron;
    }
}
