package io.kestra.core.models.triggers;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.validations.TimeWindowValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;

@Getter
@Builder
@TimeWindowValidation
public class TimeWindow {
    @Schema(
        title = "The type of the SLA",
        description = "The default SLA is a sliding window (`DURATION_WINDOW`) with a window of 24 hours."
    )
    @NotNull
    @Builder.Default
    @PluginProperty
    private TimeWindow.Type type = TimeWindow.Type.DURATION_WINDOW;

    @Schema(
        title = "SLA daily deadline",
        description = "Use it only for `DAILY_TIME_DEADLINE` SLA."
    )
    @PluginProperty
    private OffsetTime deadline;

    @Schema(
        title = "The duration of the window",
        description = """
            Use it only for `DURATION_WINDOW` or `SLIDING_WINDOW` SLA.
            See [ISO_8601 Durations](https://en.wikipedia.org/wiki/ISO_8601#Durations) for more information of available duration value.
            The start of the window is always based on midnight except if you set windowAdvance parameter. Eg if you have a 10 minutes (PT10M) window,
            the first window will be 00:00 to 00:10 and a new window will be started each 10 minutes""")
    @PluginProperty
    @With
    private Duration window;

    @Schema(
        title = "The window advance duration",
        description = """
            Use it only for `DURATION_WINDOW` SLA.
            Allow to specify the start time of the window
            Eg: you want a window of 6 hours (window=PT6H), by default the check will be done between: 00:00 and 06:00, 06:00 and 12:00, 12:00 and 18:00, and 18:00 and 00:00.
            If you want to check the window between 03:00 and 09:00, 09:00 and 15:00, 15:00 and 21:00, and 21:00 and 3:00, you will have to shift the window of 3 hours by settings windowAdvance: PT3H""")
    @PluginProperty
    @With
    private Duration windowAdvance;

    @Schema(
        title = "SLA daily start time",
        description = "Use it only for `DAILY_TIME_WINDOW` SLA."
    )
    @PluginProperty
    private OffsetTime startTime;

    @Schema(
        title = "SLA daily end time",
        description = "Use it only for `DAILY_TIME_WINDOW` SLA."
    )
    @PluginProperty
    private OffsetTime endTime;

    public enum Type {
        DAILY_TIME_DEADLINE,
        DAILY_TIME_WINDOW,
        DURATION_WINDOW,
        SLIDING_WINDOW
    }
}
