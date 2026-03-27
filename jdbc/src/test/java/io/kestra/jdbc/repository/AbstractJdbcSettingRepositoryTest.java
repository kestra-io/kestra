package io.kestra.jdbc.repository;

import org.junit.jupiter.api.BeforeEach;

import io.kestra.jdbc.JdbcTestUtils;

import jakarta.inject.Inject;

public abstract class AbstractJdbcSettingRepositoryTest extends io.kestra.core.repositories.AbstractSettingRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}