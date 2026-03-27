package io.kestra.jdbc.repository;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.jupiter.api.BeforeEach;

import io.kestra.jdbc.JdbcTestUtils;

import jakarta.inject.Inject;

public abstract class AbstractJdbcExecutionRepositoryTest extends io.kestra.core.repositories.AbstractExecutionRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Override
    protected void fetchData() {
        // TODO Remove the override once JDBC implementation has the QueryBuilder working
    }
}