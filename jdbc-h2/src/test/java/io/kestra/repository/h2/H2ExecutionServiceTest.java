package io.kestra.repository.h2;

import io.kestra.core.repositories.AbstractExecutionServiceTest;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.URISyntaxException;

class H2ExecutionServiceTest extends AbstractExecutionServiceTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}