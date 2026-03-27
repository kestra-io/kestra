package io.kestra.repository.h2;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.jupiter.api.BeforeEach;

import io.kestra.core.repositories.AbstractExecutionServiceTest;
import io.kestra.jdbc.JdbcTestUtils;

import jakarta.inject.Inject;

class H2ExecutionServiceTest extends AbstractExecutionServiceTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}