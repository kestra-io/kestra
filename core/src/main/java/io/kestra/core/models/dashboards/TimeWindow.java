package io.kestra.core.models.dashboards;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.validations.DashboardWindowValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
@DashboardWindowValidation
public class TimeWindow {
    @DurationMax(days = 366L, message = "Time window can't be more than 1 year (366 days).")
    @JsonProperty("default")
    @Schema(title = "Time range the charts show when the dashboard opens, such as `P7D`.")
    @Builder.Default
    private Duration defaultDuration = Duration.ofDays(30);

    @DurationMax(days = 366L, message = "Time window can't be more than 1 year (366 days).")
    @Schema(title = "Longest time range allowed for `default`.")
    @Builder.Default
    private Duration max = Duration.ofDays(366);
}
