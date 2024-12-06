package io.kestra.plugin.core.dashboard.chart.tables;

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
public class TableColumnDescriptor<F extends Enum<F>> extends ColumnDescriptor<F> {
    private Alignment columnAlignment = Alignment.LEFT;

    enum Alignment {
        LEFT,
        RIGHT,
        CENTER
    }
}
