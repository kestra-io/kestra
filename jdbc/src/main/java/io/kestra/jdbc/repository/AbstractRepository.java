package io.kestra.jdbc.repository;

import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;

@Singleton
public abstract class AbstractRepository {
    public Condition defaultFilter() {
        return DSL.field("deleted").eq(false);
    }
}
