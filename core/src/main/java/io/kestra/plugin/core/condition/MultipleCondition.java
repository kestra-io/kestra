package io.kestra.plugin.core.condition;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.triggers.TimeWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a flow if the list of preconditions are met in a time window.",
    description = """
        **This task is deprecated**, use io.kestra.plugin.core.condition.ExecutionsWindow or io.kestra.plugin.core.condition.FilteredExecutionsWindow instead.
        Will trigger an executions when all the flows defined by the preconditions are successfully executed in a specific period of time.
        The period is defined by the `timeSLA` property and is by default a duration window of 24 hours."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "A flow that is waiting for 2 flows to run successfully in a day",
            code = {
                "triggers:",
                "  - id: multiple-listen-flow",
                "    type: io.kestra.plugin.core.trigger.Flow",
                "    conditions:",
                "      - type: io.kestra.plugin.core.condition.ExecutionStatus",
                "        in:",
                "        - SUCCESS",
                "      - id: multiple",
                "        type: io.kestra.plugin.core.condition.MultipleCondition",
                "        sla:",
                "          window: PT12H",
                "        conditions:",
                "          flow-a:",
                "            type: io.kestra.plugin.core.condition.ExecutionFlow",
                "            namespace: io.kestra.demo",
                "            flowId: multiplecondition-flow-a",
                "          flow-b:",
                "            type: io.kestra.plugin.core.condition.ExecutionFlow",
                "            namespace: io.kestra.demo",
                "            flowId: multiplecondition-flow-b"
            }
        )
    },
    aliases = "io.kestra.core.models.conditions.types.MultipleCondition"
)
@Slf4j
@Deprecated
public class MultipleCondition extends Condition implements io.kestra.core.models.triggers.multipleflows.MultipleCondition {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    @Schema(title = "A unique id for the condition")
    @PluginProperty
    private String id;

    @Schema(
        title = "Define the time period (or window) for evaluating preconditions.",
        description = """
        You can set the `type` of `sla` to one of the following values:
        1. `DURATION_WINDOW`: this is the default `type`. It uses a start time (`windowAdvance`) and end time (`window`) that are moving forward to the next interval whenever the evaluation time reaches the end time, based on the defined duration `window`. For example, with a 1-day window (the default option: `window: PT1D`), the SLA conditions are always evaluated during 24h starting at midnight (i.e. at time 00:00:00) each day. If you set `windowAdvance: PT6H`, the window will start at 6 AM each day. If you set `windowAdvance: PT6H` and you also override the `window` property to `PT6H`, the window will start at 6 AM and last for 6 hours — as a result, Kestra will check the SLA conditions during the following time periods: 06:00 to 12:00, 12:00 to 18:00, 18:00 to 00:00, and 00:00 to 06:00, and so on.
        2. `SLIDING_WINDOW`: this option also evaluates SLA conditions over a fixed time `window`, but it always goes backward from the current time. For example, a sliding window of 1 hour (`window: PT1H`) will evaluate executions for the past hour (so between now and one hour before now). It uses a default window of 1 day.
        3. `DAILY_TIME_DEADLINE`: this option declares that some SLA conditions should be met "before a specific time in a day". With the string property `deadline`, you can configure a daily cutoff for checking conditions. For example, `deadline: "09:00:00"` means that the defined SLA conditions should be met from midnight until 9 AM each day; otherwise, the flow will not be triggered.
        4. `DAILY_TIME_WINDOW`: this option declares that some SLA conditions should be met "within a given time range in a day". For example, a window from `startTime: "06:00:00"` to `endTime: "09:00:00"` evaluates executions within that interval each day. This option is particularly useful for declarative definition of freshness conditions when building data pipelines. For example, if you only need one successful execution within a given time range to guarantee that some data has been successfully refreshed in order for you to proceed with the next steps of your pipeline, this option can be more useful than a strict DAG-based approach. Usually, each failure in your flow would block the entire pipeline, whereas with this option, you can proceed with the next steps of the pipeline as soon as the data is successfully refreshed at least once within the given time range.
        """
    )
    @PluginProperty
    @Builder.Default
    @Valid
    protected TimeWindow timeWindow = TimeWindow.builder().build();

    @Schema(
        title = "Whether to reset the evaluation results of SLA conditions after a first successful evaluation within the given time period.",
        description = """
            By default, after a successful evaluation of the set of SLA conditions, the evaluation result is reset, so, the same set of conditions needs to be successfully evaluated again in the same time period to trigger a new execution.
            This means that to create multiple executions, the same set of conditions needs to be evaluated to `true` multiple times.
            You can disable this by setting this property to `false` so that, within the same period, each time one of the conditions is satisfied again after a successful evaluation, it will trigger a new execution."""
    )
    @PluginProperty
    @NotNull
    @Builder.Default
    private Boolean resetOnSuccess = true;

    @Schema(
        title = "The duration of the window",
        description = "Deprecated, use `timeSLA.window` instead.")
    @PluginProperty
    @Deprecated
    private Duration window;

    public void setWindow(Duration window) {
        this.window = window;
        this.timeWindow = this.getTimeWindow() == null ? TimeWindow.builder().window(window).build() : this.getTimeWindow().withWindow(window);
    }

    @Schema(
        title = "The window advance duration",
        description = "Deprecated, use `timeSLA.windowAdvance` instead.")
    @PluginProperty
    @Deprecated
    private Duration windowAdvance;

    public void setWindowAdvance(Duration windowAdvance) {
        this.windowAdvance = windowAdvance;
        this.timeWindow = this.getTimeWindow() == null ? TimeWindow.builder().windowAdvance(windowAdvance).build() : this.getTimeWindow().withWindowAdvance(windowAdvance);
    }

    @NotNull
    @NotEmpty
    @Schema(
        title = "The list of preconditions to wait for",
        description = "The key must be unique for a trigger because it will be used to store the previous evaluation result."
    )
    @PluginProperty(
        additionalProperties = Condition.class
    )
    private Map<String, Condition> conditions;

    @Override
    public Logger logger() {
        return log;
    }
}
