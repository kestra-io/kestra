package io.kestra.queue.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.queue.AbstractBroadcastQueueTest;

import jakarta.inject.Inject;

@KestraTest(environments = { "test", "queue" })
@Execution(ExecutionMode.SAME_THREAD)
class PostgresBroadcastQueueTest extends AbstractBroadcastQueueTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}