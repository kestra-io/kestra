package io.kestra.jdbc.repository;

import org.junit.jupiter.api.BeforeEach;

import io.kestra.core.repositories.AbstractMetricRepositoryTest;
import io.kestra.jdbc.JdbcTestUtils;

import jakarta.inject.Inject;

public abstract class AbstractJdbcMetricRepositoryTest extends AbstractMetricRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}
