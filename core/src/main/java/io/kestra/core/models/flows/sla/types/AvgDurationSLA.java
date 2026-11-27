package io.kestra.core.models.flows.sla.types;

import io.kestra.core.models.flows.State;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.List;

@SuperBuilder
@Getter
@NoArgsConstructor
public class AvgDurationSLA extends AbstractDurationSLA {
    @DurationMin(millis = 1, message = "must be a positive duration")
    @Schema(
        title = "The duration threshold after which the SLA is considered violated",
        description = "AvgDurationSLA compute the execution duration deadline by averaging the duration of the last executions plus the duration threshold"
    )
    private Duration durationThreshold;

    @Positive
    @Schema(
        title = "The percentage threshold after which the SLA is considered violated",
        description = """
            AvgDurationSLA compute the execution duration deadline by averaging the duration of the last executions plus the percentage threshold.
            The percentage must be set as a float, for example 0.5 for 50%"""
    )
    private Float percentageThreshold;

    @Builder.Default
    @NotNull
    @Min(1)
    @Schema(
        title = "The minimum number of executions to consider when computing the average duration",
        description = "If the number of executions is lower than this value, the SLA is never triggered."
    )
    private Integer minExecutions = 5;

    @NotNull
    @Schema(title = "The lookback period to consider when computing the average duration")
    private Duration lookbackPeriod;

    @Builder.Default
    @NotNull
    @Schema(title = "The states to consider when computing the average duration")
    private List<State.Type> averageOverStates = List.of(State.Type.SUCCESS, State.Type.WARNING);

    @JsonIgnore
    @AssertTrue(message = "durationThreshold and percentageThreshold cannot both be set")
    public boolean isSingleThreshold() {
        return durationThreshold == null || percentageThreshold == null;
    }
}
