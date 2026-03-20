package io.kestra.queue.mysql;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.queue.AbstractKeyedDispatchQueueTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@KestraTest(environments =  {"test", "queue"})
@Execution(ExecutionMode.SAME_THREAD)
class MysqlKeyedDispatchQueueTest extends AbstractKeyedDispatchQueueTest {
}