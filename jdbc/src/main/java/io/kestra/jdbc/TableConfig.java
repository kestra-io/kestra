package io.kestra.jdbc;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import lombok.Getter;

@EachProperty("kestra.jdbc.tables")
@Getter
public class TableConfig {
    String name;
    Class<?> cls;
    String table;

    public TableConfig(@Parameter String name) {
        this.name = name;
    }
}
