package io.kestra.jdbc.repository;

import org.junit.jupiter.api.BeforeEach;

import io.kestra.jdbc.JdbcTestUtils;

import jakarta.inject.Inject;

public abstract class AbstractJdbcTriggerRepositoryTest extends io.kestra.core.repositories.AbstractTriggerRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    protected AbstractJdbcTriggerRepository repository;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}