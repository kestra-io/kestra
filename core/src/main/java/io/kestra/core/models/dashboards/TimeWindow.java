package io.kestra.core.models.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.validations.DashboardWindowValidation;
import io.micronaut.core.annotation.Introspected;
import lombok.*;
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
    @Builder.Default
    private Duration defaultDuration = Duration.ofDays(30);

    @DurationMax(days = 366L, message = "Time window can't be more than 1 year (366 days).")
    @Builder.Default
    private Duration max = Duration.ofDays(366);
}
