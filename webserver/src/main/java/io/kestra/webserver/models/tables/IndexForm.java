package io.kestra.webserver.models.tables;

import java.util.List;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create an index over one or more of a table's columns. */
@Introspected
@Schema(description = "Create an index on a table over one or more columns")
public record IndexForm(
    @NotBlank @Schema(description = "Index name (a valid SQL identifier)") String name,
    @NotNull @Size(min = 1) @Schema(description = "The columns the index covers, in order") List<String> columns,
    @Schema(description = "Whether the index enforces uniqueness") boolean unique) {
}
