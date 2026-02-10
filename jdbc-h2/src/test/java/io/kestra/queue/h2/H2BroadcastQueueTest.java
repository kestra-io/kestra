package io.kestra.queue.h2;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.queue.AbstractBroadcastQueueTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@MicronautTest(environments =  {"test", "queue"})
@Execution(ExecutionMode.SAME_THREAD)
class H2BroadcastQueueTest extends AbstractBroadcastQueueTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}