package io.kestra.plugin.core.dashboard.chart.kpis;

import io.kestra.core.models.dashboards.ChartOption;

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
    @Builder.Default
    private NumberType numberType = NumberType.FLAT;

    @Builder.Default
    private Color color = Color.DEFAULT;

    public enum NumberType {
        FLAT,
        PERCENTAGE
    }

    public enum Color {
        DEFAULT,
        SUCCESS,
        DANGER,
        FAILURE,
        WARNING,
        INFO
    }
}
