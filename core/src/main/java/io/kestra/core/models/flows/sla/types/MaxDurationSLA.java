package io.kestra.core.models.flows.sla.types;

import java.time.Duration;

import io.kestra.core.validations.DurationMax;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.time.DurationMin;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class MaxDurationSLA extends AbstractDurationSLA {
    @NotNull
    @DurationMax
    @DurationMin(millis = 1, message = "must be a positive duration")
    @Schema(title = "The maximum duration of the execution after which the SLA is considered violated")
    private Duration duration;
}
