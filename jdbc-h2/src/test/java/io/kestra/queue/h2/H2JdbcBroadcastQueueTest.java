package io.kestra.queue.h2;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.jdbc.queue.AbstractJdbcBroadcastQueueTest;

@KestraTest(environments = { "test", "queue" })
@Execution(ExecutionMode.SAME_THREAD)
class H2JdbcBroadcastQueueTest extends AbstractJdbcBroadcastQueueTest {
}
