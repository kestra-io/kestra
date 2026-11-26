package io.kestra.core.models.dashboards;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class ColumnDescriptor<F extends Enum<F>> {
    @Schema(title = "Field of the data source the column reads.", description = "Can be left out of a column that only counts rows with `agg: COUNT`.")
    private F field;
    @Schema(title = "Label shown for the column in the chart.")
    private String displayName;
    @Schema(
        title = "Aggregation computed for the column: `AVG`, `COUNT`, `MAX`, `MIN` or `SUM`.",
        description = "The columns without an aggregation group the rows that the aggregations are computed over."
    )
    private AggregationType agg;
    // `labelKey` is the legacy name (Executions LABELS); kept as a read alias for backward compatibility.
    @JsonAlias("labelKey")
    @Schema(title = "Key to read inside a field that holds several values.", description = "For example the label key when `field` is `LABELS`.")
    private String key;
    @Schema(title = "Key of the value that weights each row in an `AVG` aggregation.", description = "Only valid with `agg: AVG`, for the data sources that support it.")
    private String weight;
}
