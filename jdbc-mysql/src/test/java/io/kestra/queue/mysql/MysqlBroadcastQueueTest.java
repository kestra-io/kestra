package io.kestra.queue.mysql;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.queue.AbstractBroadcastQueueTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

@KestraTest(environments =  {"test", "queue"})
class MysqlBroadcastQueueTest extends AbstractBroadcastQueueTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}