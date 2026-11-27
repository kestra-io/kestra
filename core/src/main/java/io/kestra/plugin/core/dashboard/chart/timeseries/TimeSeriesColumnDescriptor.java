package io.kestra.plugin.core.dashboard.chart.timeseries;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.GraphStyle;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Schema
public class TimeSeriesColumnDescriptor<F extends Enum<F>> extends ColumnDescriptor<F> {
    @Schema(title = "How the column is drawn: `LINES`, `BARS` or `POINTS`.", description = "An aggregated column with no style is drawn as `LINES`.")
    private GraphStyle graphStyle;

    public GraphStyle getGraphStyle() {
        if (graphStyle == null && this.getAgg() != null) {
            return GraphStyle.LINES;
        }

        return graphStyle;
    }
}
