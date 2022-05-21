package io.kestra.jdbc.repository;

import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractJdbcTriggerRepositoryTest extends io.kestra.core.repositories.AbstractTriggerRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}