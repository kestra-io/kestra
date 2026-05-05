package io.kestra.core.models.triggers;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.validations.WindowValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalTime;

@Builder
@Getter
@WindowValidation
@Schema(
    title = "Window configuration",
    description = """
        Defines the time window within which all `dependsOn` conditions must be met for the trigger to fire.
        The window type is inferred from the fields that are set:
        - `deadline` set: daily time deadline window (conditions must be met before the given time each day).
        - `from` and `to` both set: daily time window (conditions must be met within the given time range each day).
        - `lookback` set: sliding window (conditions must be met within the past duration).
        - otherwise: duration window (default, conditions must be met within a fixed duration, configurable via `every` and `offset`)."""
)
public class Window {
    @Schema(
        title = "Daily deadline",
        description = "Use this to define a `DAILY_TIME_DEADLINE` window: the `dependsOn` conditions must be met before this time each day. Mutually exclusive with `from`, `to`, `lookback`, `every`, and `offset`."
    )
    @PluginProperty
    private LocalTime deadline;

    @Schema(
        title = "Daily window start time",
        description = "Use this together with `to` to define a `DAILY_TIME_WINDOW`: the `dependsOn` conditions must be met within the time range `[from, to]` each day. Mutually exclusive with `deadline`, `lookback`, `every`, and `offset`."
    )
    @PluginProperty
    private LocalTime from;

    @Schema(
        title = "Daily window end time",
        description = "Use this together with `from` to define a `DAILY_TIME_WINDOW`: the `dependsOn` conditions must be met within the time range `[from, to]` each day. Mutually exclusive with `deadline`, `lookback`, `every`, and `offset`."
    )
    @PluginProperty
    private LocalTime to;

    @Schema(
        title = "Duration window size",
        description = "Use this to define the size of a `DURATION_WINDOW`: the `dependsOn` conditions must be met within a fixed-duration window that advances at the given interval. Defaults to 1 day. Mutually exclusive with `deadline`, `from`, `to`, and `lookback`."
    )
    @PluginProperty
    private Duration every;

    @Schema(
        title = "Duration window offset",
        description = "Use this to shift the start of the `DURATION_WINDOW` relative to midnight. For example, `PT6H` shifts the window start by 6 hours when combined with a 1-day `every`. Mutually exclusive with `deadline`, `from`, `to`, and `lookback`."
    )
    @PluginProperty
    private Duration offset;

    @Schema(
        title = "Sliding window lookback duration",
        description = "Use this to define a `SLIDING_WINDOW`: the `dependsOn` conditions must be met within the past duration relative to the current time. Mutually exclusive with `deadline`, `from`, `to`, `every`, and `offset`."
    )
    @PluginProperty
    private Duration lookback;

    @Schema(
        title = "Whether the trigger can fire only once per window",
        description = """
            When `false` (the default), the window state is NOT reset after a successful evaluation, meaning the trigger can fire again within the same window each time conditions are satisfied.
            When `true`, after a successful evaluation the window state is reset, so the same set of conditions must be met again within the window to trigger a new execution."""
    )
    @Builder.Default
    @PluginProperty
    @NotNull
    private boolean fireOnce = false;

    /**
     * Converts this {@code Window} to a {@link TimeWindow}.
     * <p>
     * The {@link TimeWindow.Type} is inferred from the fields that are set:
     * <ul>
     *   <li>{@code deadline} set → {@link TimeWindow.Type#DAILY_TIME_DEADLINE}</li>
     *   <li>{@code from} and {@code to} both set → {@link TimeWindow.Type#DAILY_TIME_WINDOW}</li>
     *   <li>{@code lookback} set → {@link TimeWindow.Type#SLIDING_WINDOW}</li>
     *   <li>otherwise → {@link TimeWindow.Type#DURATION_WINDOW} (with {@code every} as window and {@code offset} as windowAdvance)</li>
     * </ul>
     *
     * @return a {@link TimeWindow} equivalent of this window
     */
    public TimeWindow toTimeWindow() {
        if (deadline != null) {
            return TimeWindow.builder()
                .type(TimeWindow.Type.DAILY_TIME_DEADLINE)
                .deadline(deadline)
                .build();
        }
        if (from != null && to != null) {
            return TimeWindow.builder()
                .type(TimeWindow.Type.DAILY_TIME_WINDOW)
                .startTime(from)
                .endTime(to)
                .build();
        }
        if (lookback != null) {
            return TimeWindow.builder()
                .type(TimeWindow.Type.SLIDING_WINDOW)
                .window(lookback)
                .build();
        }
        return TimeWindow.builder()
            .type(TimeWindow.Type.DURATION_WINDOW)
            .window(every)
            .windowAdvance(offset)
            .build();
    }
}
