package io.kestra.jdbc.repository;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import javax.inject.Singleton;

@Singleton
public abstract class AbstractRepository {
    public Condition defaultFilter() {
        return DSL.field("deleted").eq(false);
    }
}
