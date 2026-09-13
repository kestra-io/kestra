package io.kestra.webserver.models.tables;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How many rows a CSV import inserted.
 *
 * <p>
 * The import is all-or-nothing, so this is either the full count or the request failed and nothing
 * was inserted. There is no partial number to report.
 */
@Introspected
@Schema(description = "Result of a CSV row import")
public record CsvImportVo(
    @Schema(description = "Number of rows inserted") int inserted) {
}
