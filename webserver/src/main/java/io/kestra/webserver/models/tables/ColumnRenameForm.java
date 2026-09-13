package io.kestra.webserver.models.tables;

import io.kestra.fethr.table.validation.ColumnNameCheck;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/** Rename a column. The column being renamed comes from the path. */
@Introspected
@Schema(description = "Rename a table column")
public record ColumnRenameForm(
    @ColumnNameCheck @Schema(description = "New column name (a valid SQL identifier)") String name) {
}
