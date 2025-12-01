package io.kestra.queue.postgres;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.queue.AbstractVNodeDispatchQueueTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

@KestraTest(environments =  {"test", "queue"})
class PostgresVNodeDispatchQueueTest extends AbstractVNodeDispatchQueueTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}