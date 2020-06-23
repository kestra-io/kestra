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
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.schedulers.validations.CronExpression;

import java.sql.Date;
import java.time.Duration;
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
public class Schedule extends AbstractTrigger implements PollingTriggerInterface {
    @NotNull
    @CronExpression
    private String cron;

    private ScheduleBackfill backfill;

    public ZonedDateTime nextDate(Optional<? extends TriggerContext> last) {
        if (this.backfill == null) {
            return computeNextDate(ZonedDateTime.now(ZoneId.systemDefault()));
        }

        if (last.isEmpty()) {
            return backfill.getStart();
        } else {
            return computeNextDate(last.get().getDate());
        }
    }

    public Optional<Execution> evaluate(TriggerContext context) {
        // Predictor returns next date when cron is on current Instant.
        // The date match is done on previous second.
        ZonedDateTime next = zonedDateTime(new Predictor(
            cron,
            Date.from(context.getDate()
                .minus(Duration.ofSeconds(1))
                .toInstant())
        )
            .nextMatchingDate()
            .getTime());

        boolean isReady = next.toEpochSecond() == context.getDate().toEpochSecond();

        if (!isReady) {
            return Optional.empty();
        }

        if (next.toEpochSecond() > ZonedDateTime.now(ZoneId.systemDefault()).toEpochSecond()) {
            return Optional.empty();
        }

        Execution execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(context.getFlowRevision())
            .state(new State())
            .variables(ImmutableMap.of(
                "schedule", ImmutableMap.of(
                    "date", next,
                    "next", computeNextDate(next)
                )
            ))
            .build();

        return Optional.of(execution);
    }

    private ZonedDateTime computeNextDate(ZonedDateTime date) {
        return zonedDateTime(new Predictor(cron, date.toInstant().toEpochMilli()).nextMatchingDate().getTime());
    }

    private static ZonedDateTime zonedDateTime(long millis) {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault());
    }
}
