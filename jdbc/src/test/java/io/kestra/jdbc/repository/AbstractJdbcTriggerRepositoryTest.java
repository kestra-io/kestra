package io.kestra.jdbc.repository;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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