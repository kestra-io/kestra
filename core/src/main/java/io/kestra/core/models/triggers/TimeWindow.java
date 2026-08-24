package io.kestra.core.models.triggers;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.validations.TimeWindowValidation;
import io.kestra.core.validations.TimezoneId;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import static io.kestra.core.models.triggers.TimeWindow.Type.DURATION_WINDOW;

@Getter
@Builder
@TimeWindowValidation
public class TimeWindow {
    @Schema(
        title = "The type of the SLA",
        description = "The default SLA is a sliding window (`DURATION_WINDOW`) with a window of 24 hours."
    )
    @Builder.Default
    @PluginProperty
    private TimeWindow.Type type = TimeWindow.Type.DURATION_WINDOW;

    @Schema(
        title = "SLA daily deadline",
        description = "Use it only for `DAILY_TIME_DEADLINE` SLA."
    )
    @PluginProperty
    private LocalTime deadline;

    @Schema(
        title = "The duration of the window",
        description = """
            Use it only for `DURATION_WINDOW` or `SLIDING_WINDOW` SLA.
            See [ISO_8601 Durations](https://en.wikipedia.org/wiki/ISO_8601#Durations) for more information of available duration value.
            The start of the window is always based on midnight except if you set windowAdvance parameter. Eg if you have a 10 minutes (PT10M) window,
            the first window will be 00:00 to 00:10 and a new window will be started each 10 minutes"""
    )
    @PluginProperty
    @With
    private Duration window;

    @Schema(
        title = "The window advance duration",
        description = """
            Use it only for `DURATION_WINDOW` SLA.
            Allow to specify the start time of the window
            Eg: you want a window of 6 hours (window=PT6H), by default the check will be done between: 00:00 and 06:00, 06:00 and 12:00, 12:00 and 18:00, and 18:00 and 00:00.
            If you want to check the window between 03:00 and 09:00, 09:00 and 15:00, 15:00 and 21:00, and 21:00 and 3:00, you will have to shift the window of 3 hours by settings windowAdvance: PT3H"""
    )
    @PluginProperty
    @With
    private Duration windowAdvance;

    @Schema(
        title = "SLA daily start time",
        description = "Use it only for `DAILY_TIME_WINDOW` SLA."
    )
    @PluginProperty
    private LocalTime startTime;

    @Schema(
        title = "SLA daily end time",
        description = "Use it only for `DAILY_TIME_WINDOW` SLA."
    )
    @PluginProperty
    private LocalTime endTime;

    @Schema(
        title = "The timezone used to resolve the daily deadline, start and end times",
        description = "Defaults to the server timezone. Set a time-zone ID such as `Europe/Paris` so that daily windows follow the intended zone, including daylight-saving transitions."
    )
    @PluginProperty
    @TimezoneId
    private String timezone;

    /** The zone in which the daily deadline, start and end times are resolved; the server default when unset. */
    public ZoneId zoneId() {
        return timezone != null ? ZoneId.of(timezone) : ZoneId.systemDefault();
    }

    /**
     * Computes the concrete start/end instants of this window relative to {@code now}.
     * <p>
     * Always resolves in {@link #zoneId()} regardless of {@code now}'s own zone -- {@code now} is
     * re-expressed in that zone first. The two {@code DAILY_*} types then resolve
     * {@code deadline}/{@code startTime}/{@code endTime} with a null preferred offset (via
     * {@link ZonedDateTime#of} and {@link java.time.LocalDate#atStartOfDay(ZoneId)}), so the
     * result depends only on the date, the configured time and the zone -- never on what offset
     * {@code now} itself happens to carry across a daylight-saving transition.
     */
    public Pair<ZonedDateTime, ZonedDateTime> boundaries(ZonedDateTime now) {
        ZoneId zone = zoneId();
        now = now.withZoneSameInstant(zone);
        Type resolvedType = type != null ? type : DURATION_WINDOW;

        return switch (resolvedType) {
            case DURATION_WINDOW -> {
                Duration windowDuration = window == null ? Duration.ofDays(1) : window;
                if (windowDuration.toDays() > 0) {
                    now = now.withHour(0);
                }

                if (windowDuration.toHours() > 0) {
                    now = now.withMinute(0);
                }

                if (windowDuration.toMinutes() > 0) {
                    now = now.withSecond(0)
                        .withMinute(0)
                        .plusMinutes(windowDuration.toMinutes() * (now.getMinute() / windowDuration.toMinutes()));
                }

                ZonedDateTime startWindow = windowAdvance == null ? now : now.plus(windowAdvance).truncatedTo(ChronoUnit.MILLIS);
                yield Pair.of(
                    startWindow,
                    startWindow.plus(windowDuration).minus(Duration.ofMillis(1)).truncatedTo(ChronoUnit.MILLIS)
                );
            }
            case SLIDING_WINDOW -> Pair.of(
                now.truncatedTo(ChronoUnit.MILLIS),
                now.truncatedTo(ChronoUnit.MILLIS).plus(window == null ? Duration.ofDays(1) : window)
            );
            case DAILY_TIME_WINDOW -> Pair.of(
                ZonedDateTime.of(now.toLocalDate(), startTime, zone).truncatedTo(ChronoUnit.MILLIS),
                ZonedDateTime.of(now.toLocalDate(), endTime, zone).truncatedTo(ChronoUnit.MILLIS)
            );
            case DAILY_TIME_DEADLINE -> Pair.of(
                now.toLocalDate().atStartOfDay(zone),
                ZonedDateTime.of(now.toLocalDate(), deadline, zone).truncatedTo(ChronoUnit.MILLIS)
            );
        };
    }

    public enum Type {
        DAILY_TIME_DEADLINE,
        DAILY_TIME_WINDOW,
        DURATION_WINDOW,
        SLIDING_WINDOW
    }
}
