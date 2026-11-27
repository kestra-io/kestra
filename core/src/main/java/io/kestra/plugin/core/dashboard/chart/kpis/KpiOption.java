package io.kestra.plugin.core.dashboard.chart.kpis;

import io.kestra.core.models.dashboards.ChartOption;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class KpiOption extends ChartOption {
    @Schema(
        title = "How the KPI value is shown: as a raw number (`FLAT`) or as a percentage (`PERCENTAGE`).",
        description = "A percentage is the value matching `where` and `numerator` divided by the value matching `where` alone."
    )
    @Builder.Default
    private NumberType numberType = NumberType.FLAT;

    public enum NumberType {
        FLAT,
        PERCENTAGE
    }
}
