package io.kestra.webserver.models.tables;

import java.time.Instant;

import io.kestra.fethr.table.ColumnDataType;
import io.kestra.fethr.table.TableDefinition;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A table's identity, without its columns or indexes.
 *
 * <p>
 * The physical table name is deliberately absent. It is an internal detail, and exposing it would
 * hand a caller the real identifier behind a table they are only supposed to reach by its logical
 * name.
 */
@Introspected
@Schema(description = "Table metadata (without columns or indexes)")
public record TableVo(
    String namespace,
    String name,
    String description,
    ColumnDataType primaryKeyType,
    Instant created,
    Instant updated) {
    public static TableVo of(TableDefinition table) {
        return new TableVo(
            table.getNamespace(),
            table.getName(),
            table.getDescription(),
            table.getPrimaryKeyType(),
            table.getCreated(),
            table.getUpdated()
        );
    }
}
