package io.kestra.core.models.flows.sla.types;

import io.kestra.core.models.flows.State;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private Duration durationThreshold;

    @Positive
    private Float percentageThreshold;

    @Builder.Default
    @NotNull
    @Min(1)
    private Integer minExecutions = 5;

    @NotNull
    private Duration lookbackPeriod;

    @Builder.Default
    @NotNull
    private List<State.Type> averageOverStates = List.of(State.Type.SUCCESS, State.Type.WARNING);

    @JsonIgnore
    @AssertTrue(message = "durationThreshold and percentageThreshold cannot both be set")
    public boolean isSingleThreshold() {
        return durationThreshold == null || percentageThreshold == null;
    }

    @Override
    protected String durationKind() {
        return "average";
    }
}
