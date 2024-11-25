package io.kestra.core.models.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.validations.DashboardWindowValidation;
import io.micronaut.core.annotation.Introspected;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.time.DurationMax;

import java.time.Duration;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
@DashboardWindowValidation
public class TimeWindow {
    @DurationMax(days = 366L, message = "Time window can't be more than 1 year (366 days).")
    @JsonProperty("default")
    private Duration defaultDuration;

    @DurationMax(days = 366L, message = "Time window can't be more than 1 year (366 days).")
    private Duration max;
}
