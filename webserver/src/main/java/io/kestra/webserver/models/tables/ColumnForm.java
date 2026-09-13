package io.kestra.webserver.models.tables;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.kestra.fethr.table.ColumnDataType;
import io.kestra.fethr.table.validation.ColumnDataTypeCheck;
import io.kestra.fethr.table.validation.ColumnNameCheck;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * A column on a create-table request.
 *
 * <p>
 * An unknown type name reads as null and is refused by {@link ColumnDataTypeCheck}, rather than
 * failing in Jackson where it would surface as a parse error instead of a validation one.
 */
@Introspected
@Schema(description = "A column definition for a user-defined table")
public record ColumnForm(
    @NotBlank @ColumnNameCheck @Schema(description = "Column name (letters, digits, underscore)") String name,

    @ColumnDataTypeCheck
    @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
    @Schema(description = "Column data type") ColumnDataType type,

    @Schema(description = "Whether the column accepts NULL") boolean nullable,
    @Schema(description = "Mask the value in the UI. Not encryption: the value is stored in the clear") boolean sensitive,
    @Schema(description = "Placeholder or help text") String hint,
    @Schema(description = "Default value expression") String defaultValue) {
}
