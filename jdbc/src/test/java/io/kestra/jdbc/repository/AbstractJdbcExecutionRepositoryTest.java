package io.kestra.jdbc.repository;

import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.URISyntaxException;

public abstract class AbstractJdbcExecutionRepositoryTest extends io.kestra.core.repositories.AbstractExecutionRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}