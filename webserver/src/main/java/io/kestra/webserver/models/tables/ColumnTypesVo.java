package io.kestra.webserver.models.tables;

import java.util.Arrays;
import java.util.List;

import io.kestra.fethr.table.ColumnDataType;

import io.micronaut.core.annotation.Introspected;

/**
 * The column-type catalogue: every type, and the subset that can back a primary key.
 *
 * <p>
 * Derived from the enum rather than listed, so the UI's options cannot fall out of step with what
 * the DDL layer will actually accept.
 */
@Introspected
public record ColumnTypesVo(
    List<String> columnDataTypes,
    List<String> primaryKeyTypes) {
    public static ColumnTypesVo fromEnum() {
        return new ColumnTypesVo(
            Arrays.stream(ColumnDataType.values()).map(Enum::name).toList(),
            Arrays.stream(ColumnDataType.values()).filter(ColumnDataType::isPrimaryKey).map(Enum::name).toList()
        );
    }
}
