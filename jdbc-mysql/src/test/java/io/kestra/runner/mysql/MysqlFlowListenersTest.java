package io.kestra.runner.mysql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.FlowListenersTest;
import io.kestra.jdbc.JdbcTestUtils;

import jakarta.inject.Inject;

class MysqlFlowListenersTest extends FlowListenersTest {
    @Inject
    FlowListeners flowListenersService;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Test
    public void all() {
        this.suite(flowListenersService);
    }

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}