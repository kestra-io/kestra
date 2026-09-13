package io.kestra.webserver.models.tables;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.kestra.fethr.table.ColumnDataType;
import io.kestra.fethr.table.validation.ColumnDataTypeCheck;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/** Change a column's data type. The column comes from the path. */
@Introspected
@Schema(description = "Change a table column's data type")
public record ColumnTypeForm(
    @ColumnDataTypeCheck
    @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
    @Schema(description = "New column data type") ColumnDataType type) {
}
