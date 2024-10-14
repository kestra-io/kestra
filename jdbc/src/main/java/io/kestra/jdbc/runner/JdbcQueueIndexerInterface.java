package io.kestra.jdbc.runner;

import org.jooq.DSLContext;

public interface JdbcQueueIndexerInterface<T> {
    T save(DSLContext context, T message);
}
