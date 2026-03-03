package io.kestra.core.queues;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.runners.*;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startWorker = false)
@Property(name = "kestra.server-type", value = "EXECUTOR")
public abstract class AbstractQueueLagTest {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    protected QueueInterface<WorkerJob> workerJobQueue;

    @Inject
    private RunContextFactory runContextFactory;

    private static final String TEST_CONSUMER_GROUP_NAME = "test-group";
    private static final String NO_LAG_TEST_WORKER_GROUP_NAME = "no-lag-test-group";

    @Test
    void shouldReturnZeroLag_whenAllMessagesConsumed() throws Exception {
        // Given
        CountDownLatch consumedLatch = new CountDownLatch(1);
        Runnable closeConsumer = workerJobQueue.receive(NO_LAG_TEST_WORKER_GROUP_NAME, Worker.class, either -> {
            consumedLatch.countDown();
        }, true);

        workerJobQueue.emit(NO_LAG_TEST_WORKER_GROUP_NAME, buildWorkerJob("io.kestra.lag.test"));
        consumedLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Thread.sleep(1000);
        closeConsumer.run();

        // When
        Integer lag = workerJobQueue.queueLagForConsumerGroup(NO_LAG_TEST_WORKER_GROUP_NAME, Worker.class);

        // Then
        assertThat(lag).isNotNull();
        assertThat(lag).isEqualTo(0);
    }

    @Test
    void shouldReturnPositiveLag_whenMessagesProducedAfterConsumerStopped() throws Exception {
        // Given
        String consumerGroup = IdUtils.create();
        CountDownLatch consumedLatch = new CountDownLatch(1);
        Runnable closeConsumer = workerJobQueue.receive(consumerGroup, Worker.class, either -> {
            consumedLatch.countDown();
        }, true);

        workerJobQueue.emit(consumerGroup, buildWorkerJob("io.kestra.lag.test"));
        consumedLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Thread.sleep(1000);
        closeConsumer.run();

        workerJobQueue.emit(consumerGroup, buildWorkerJob("io.kestra.lag.test.new"));
        Thread.sleep(1000);

        // When
        Integer lag = workerJobQueue.queueLagForConsumerGroup(consumerGroup, Worker.class);

        // Then
        assertThat(lag).isNotNull();
        assertThat(lag).isEqualTo(1);
    }

    private WorkerJob buildWorkerJob(String namespace) {
        Return task = Return.builder()
            .id("test-" + IdUtils.create())
            .type(Return.class.getName())
            .format(io.kestra.core.models.property.Property.of("test"))
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace(namespace == null ? "kestra.test" : namespace)
            .tasks(Collections.singletonList(task))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        ResolvedTask resolvedTask = ResolvedTask.of(task);

        return WorkerTask.builder()
            .runContext(runContextFactory.of(ImmutableMap.of()))
            .task(task)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }


    @MockBean
    @Replaces(WorkerGroupExecutorInterface.class)
    WorkerGroupExecutorInterface workerGroupExecutorInterface() {
        WorkerGroupExecutorInterface workerGroupExecutorInterface = Mockito.mock(WorkerGroupExecutorInterface.class);
        Mockito.when(workerGroupExecutorInterface.listAllWorkerGroupKeys()).thenReturn(
            Set.of(TEST_CONSUMER_GROUP_NAME, NO_LAG_TEST_WORKER_GROUP_NAME)
        );

        return workerGroupExecutorInterface;
    }
}
