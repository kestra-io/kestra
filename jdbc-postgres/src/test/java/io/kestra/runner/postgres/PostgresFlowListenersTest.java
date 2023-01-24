package io.kestra.runner.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.FlowListenersTest;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgresFlowListenersTest extends FlowListenersTest {
    @Inject
    FlowListeners flowListenersService;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Test
    public void all() throws JsonProcessingException {
        this.suite(flowListenersService);
    }

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}