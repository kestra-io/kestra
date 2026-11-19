package io.kestra.plugin.core.trigger;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.cronutils.model.time.ExecutionTime;

/**
 * An {@link ExecutionTime} that keeps one run for a daylight-saving spring-forward gap in which the cron matches,
 * since cron-utils skips local times that do not exist. The run is the first matching local time shifted forward by
 * the length of the gap, as {@link ZonedDateTime#ofLocal} resolves it, and is dropped when that instant is already a
 * regular run.
 */
final class DstGapExecutionTime implements ExecutionTime {
    // The longest gap ever observed is a day (Samoa, 2011), so a run after a date comes from a gap starting at most a day before it.
    private static final Duration MAX_GAP = Duration.ofDays(1);

    private final ExecutionTime delegate;

    DstGapExecutionTime(ExecutionTime delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<ZonedDateTime> nextExecution(ZonedDateTime date) {
        Optional<ZonedDateTime> next = delegate.nextExecution(date);
        if (next.isEmpty()) {
            return next;
        }

        Instant after = date.toInstant();
        Instant before = next.get().toInstant();
        return gapRuns(date.getZone(), after.minus(MAX_GAP), before).stream()
            .filter(run -> run.toInstant().isAfter(after) && run.toInstant().isBefore(before))
            .findFirst()
            .or(() -> next);
    }

    @Override
    public Optional<Duration> timeToNextExecution(ZonedDateTime date) {
        return nextExecution(date).map(next -> Duration.between(date, next));
    }

    @Override
    public Optional<ZonedDateTime> lastExecution(ZonedDateTime date) {
        Optional<ZonedDateTime> last = delegate.lastExecution(date);
        Instant before = date.toInstant();
        Optional<Instant> after = last.map(ZonedDateTime::toInstant);

        return gapRuns(date.getZone(), after.orElse(before).minus(MAX_GAP), before).stream()
            .filter(run -> run.toInstant().isBefore(before) && after.map(run.toInstant()::isAfter).orElse(true))
            .reduce((previous, current) -> current)
            .or(() -> last);
    }

    @Override
    public Optional<Duration> timeFromLastExecution(ZonedDateTime date) {
        return lastExecution(date).map(last -> Duration.between(last, date));
    }

    @Override
    public boolean isMatch(ZonedDateTime date) {
        return delegate.isMatch(date) || nextExecution(date.minusSeconds(1)).filter(date::isEqual).isPresent();
    }

    private List<ZonedDateTime> gapRuns(ZoneId zone, Instant from, Instant to) {
        ZoneRules rules = zone.getRules();
        List<ZonedDateTime> runs = new ArrayList<>();
        for (ZoneOffsetTransition transition = rules.nextTransition(from); transition != null && !transition.getInstant().isAfter(to); transition = rules.nextTransition(transition.getInstant())) {
            if (transition.isGap()) {
                gapRun(zone, transition).ifPresent(runs::add);
            }
        }
        return runs;
    }

    private Optional<ZonedDateTime> gapRun(ZoneId zone, ZoneOffsetTransition gap) {
        // The gap's local times do not exist in the zone, so the cron is matched against them at the offset in force before it.
        ZonedDateTime gapStart = gap.getDateTimeBefore().atZone(gap.getOffsetBefore());
        return delegate.nextExecution(gapStart.minusSeconds(1))
            .map(ZonedDateTime::toLocalDateTime)
            .filter(local -> local.isBefore(gap.getDateTimeAfter()))
            .map(local -> ZonedDateTime.ofLocal(local, zone, null))
            .filter(run -> !delegate.isMatch(run));
    }
}
