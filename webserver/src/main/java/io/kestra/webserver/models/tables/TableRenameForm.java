package io.kestra.webserver.models.tables;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.fethr.table.validation.TableName;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Rename a table and change its description.
 *
 * <p>
 * A merge patch: an absent field keeps its current value, so both are nullable and nulls are not
 * serialized.
 */
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Rename a table and/or change its description")
public record TableRenameForm(
    @Nullable @TableName @Schema(description = "New table name") String name,
    @Nullable @Schema(description = "New description") String description) {
}
