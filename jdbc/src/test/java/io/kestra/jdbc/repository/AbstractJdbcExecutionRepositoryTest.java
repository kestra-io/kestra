package io.kestra.jdbc.repository;

public abstract class AbstractJdbcExecutionRepositoryTest extends io.kestra.core.repositories.AbstractExecutionRepositoryTest {
    @Override
    protected void fetchData() {
        // TODO Remove the override once JDBC implementation has the QueryBuilder working
    }
}