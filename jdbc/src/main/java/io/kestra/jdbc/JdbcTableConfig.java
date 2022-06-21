package io.kestra.jdbc;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import lombok.Getter;

@EachProperty("kestra.jdbc.tables")
@Getter
public class JdbcTableConfig {
    String name;
    Class<?> cls;
    String table;

    public JdbcTableConfig(@Parameter String name) {
        this.name = name;
    }
}
