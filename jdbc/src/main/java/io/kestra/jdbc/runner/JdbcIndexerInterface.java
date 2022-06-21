package io.kestra.jdbc.runner;

import org.jooq.DSLContext;

public interface JdbcIndexerInterface<T> {
    T save(DSLContext context, T message);
}
