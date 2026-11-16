package io.kestra.core.models.dashboards;

import java.io.Serializable;
import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * What a chart's data source tells about the rows it returned, for the data sources that read an execution output.
 *
 * @param date when the execution ended, which is when its data was produced
 * @param rowsRead how many rows the data source read from the data, before any filter
 * @param truncated whether the data source stopped reading before the end of the data because of its limit
 */
public record ChartMetadata(@NotNull String executionId, @NotNull Instant date, @NotNull String state, int rowsRead, boolean truncated) implements Serializable {
}
