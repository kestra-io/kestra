package io.kestra.queue.mysql;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.queue.AbstractKeyedDispatchQueueTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

@KestraTest(environments =  {"test", "queue"})
class MysqlKeyedDispatchQueueTest extends AbstractKeyedDispatchQueueTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}