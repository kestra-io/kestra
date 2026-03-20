package io.kestra.queue.h2;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.queue.AbstractVNodeDispatchQueueTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@KestraTest(environments =  {"test", "queue"})
@Execution(ExecutionMode.SAME_THREAD)
class H2VNodeDispatchQueueTest extends AbstractVNodeDispatchQueueTest {
}