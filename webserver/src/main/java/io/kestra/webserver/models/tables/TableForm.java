package io.kestra.webserver.models.tables;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.kestra.fethr.table.ColumnDataType;
import io.kestra.fethr.table.validation.PrimaryKeyType;
import io.kestra.fethr.table.validation.TableName;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create a user-defined table. The namespace comes from the path, not the body. */
@Introspected
@Schema(description = "Create a user-defined table")
public record TableForm(
    @NotBlank @TableName @Schema(description = "Logical table name (a valid SQL identifier)") String name,
    @Schema(description = "Optional description") String description,

    @NotNull
    @PrimaryKeyType
    @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
    @Schema(description = "Primary key column type (UUID or NUMBER)") ColumnDataType primaryKeyType,

    @NotNull @Size(min = 1) @Schema(description = "The table columns, in order") List<@Valid ColumnForm> columns) {
}
