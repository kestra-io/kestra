package io.kestra.webserver.models.tables;

import java.time.Instant;
import java.util.List;

import io.kestra.fethr.table.ColumnDataType;
import io.kestra.fethr.table.ColumnDefinition;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.fethr.table.TableIndexDefinition;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A table's full schema: its identity plus columns and indexes.
 *
 * <p>
 * The fork had this extend {@link TableVo} to share the header fields. Here both are records, which
 * cannot extend one another, so the header is restated -- six fields against the inheritance that
 * would otherwise force both to be classes with builders.
 */
@Introspected
@Schema(description = "A table's full schema (metadata, columns and indexes)")
public record TableDetailVo(
    String namespace,
    String name,
    String description,
    ColumnDataType primaryKeyType,
    Instant created,
    Instant updated,
    @Schema(description = "The table columns, in order") List<ColumnDefinition> columns,
    @Schema(description = "The table indexes") List<TableIndexDefinition> indexes) {
    public static TableDetailVo of(TableDefinition table) {
        return new TableDetailVo(
            table.getNamespace(),
            table.getName(),
            table.getDescription(),
            table.getPrimaryKeyType(),
            table.getCreated(),
            table.getUpdated(),
            table.getColumns(),
            table.getIndexes()
        );
    }
}
