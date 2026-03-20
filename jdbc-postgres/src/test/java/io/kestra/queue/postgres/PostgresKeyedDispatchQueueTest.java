package io.kestra.queue.postgres;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.queue.AbstractKeyedDispatchQueueTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@KestraTest(environments =  {"test", "queue"})
@Execution(ExecutionMode.SAME_THREAD)
class PostgresKeyedDispatchQueueTest extends AbstractKeyedDispatchQueueTest {
}