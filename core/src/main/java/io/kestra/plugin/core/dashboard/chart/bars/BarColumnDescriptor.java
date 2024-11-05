package io.kestra.plugin.core.dashboard.chart.bars;

import io.kestra.core.models.dashboards.ColumnDescriptor;
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
public class BarColumnDescriptor<F extends Enum<F>> extends ColumnDescriptor<F> {
    private Integer limit;
}
