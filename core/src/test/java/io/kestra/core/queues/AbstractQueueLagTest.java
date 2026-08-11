package io.kestra.core.queues;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startWorker = false)
@Property(name = "kestra.server-type", value = "EXECUTOR")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
public abstract class AbstractQueueLagTest {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    @Inject
    protected KeyedDispatchQueueInterface<WorkerJobEvent> workerJobQueue;

    @Inject
    private RunContextFactory runContextFactory;

    private static final String TEST_CONSUMER_GROUP_NAME = "test-group";
    private static final String NO_LAG_TEST_WORKER_GROUP_NAME = "no-lag-test-group";

    @Test
    void shouldReturnZeroLag_whenAllMessagesConsumed() throws Exception {
        // Given
        CountDownLatch consumedLatch = new CountDownLatch(1);
        QueueSubscriber<WorkerJobEvent> closeConsumer = workerJobQueue.subscriber(NO_LAG_TEST_WORKER_GROUP_NAME).subscribe(either ->
        {
            consumedLatch.countDown();
        });

        workerJobQueue.emit(NO_LAG_TEST_WORKER_GROUP_NAME, buildWorkerJob("io.kestra.lag.test", NO_LAG_TEST_WORKER_GROUP_NAME));
        assertTrue(consumedLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        closeConsumer.close();

        // When
        Integer lag = workerJobQueue.queueLag(NO_LAG_TEST_WORKER_GROUP_NAME);

        // Then
        assertThat(lag).isNotNull();
        assertThat(lag).isEqualTo(0);
    }

    @Test
    void shouldReturnPositiveLag_whenMessagesProducedAfterConsumerStopped() throws Exception {
        // Given
        CountDownLatch consumedLatch = new CountDownLatch(1);
        QueueSubscriber<WorkerJobEvent> closeConsumer = workerJobQueue.subscriber(NO_LAG_TEST_WORKER_GROUP_NAME).subscribe(either ->
        {
            consumedLatch.countDown();
        });

        workerJobQueue.emit(NO_LAG_TEST_WORKER_GROUP_NAME, buildWorkerJob("io.kestra.lag.test", NO_LAG_TEST_WORKER_GROUP_NAME));
        assertTrue(consumedLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        closeConsumer.close();

        workerJobQueue.emit(NO_LAG_TEST_WORKER_GROUP_NAME, buildWorkerJob("io.kestra.lag.test.new", NO_LAG_TEST_WORKER_GROUP_NAME));

        // When
        Integer lag = workerJobQueue.queueLag(NO_LAG_TEST_WORKER_GROUP_NAME);

        // Then
        assertThat(lag).isNotNull();
        assertThat(lag).isEqualTo(1);
    }

    private WorkerJobEvent buildWorkerJob(String namespace, String workerGroup) {
        Return task = Return.builder()
            .id("test-" + IdUtils.create())
            .type(Return.class.getName())
            .format(io.kestra.core.models.property.Property.ofValue("test"))
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace(namespace == null ? "kestra.test" : namespace)
            .tasks(Collections.singletonList(task))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        ResolvedTask resolvedTask = ResolvedTask.of(task);

        return WorkerJobEvent.of(
            WorkerTask.builder()
                .data(WorkerTaskData.from(runContextFactory.of(ImmutableMap.of())))
                .task(task)
                .taskRun(TaskRun.of(execution, resolvedTask))
                .build(),
            workerGroup
        );
    }

    @MockBean
    @Replaces(WorkerQueueMetaStore.class)
    WorkerQueueMetaStore workerGroupExecutorInterface() {
        WorkerQueueMetaStore workerGroupExecutorInterface = Mockito.mock(WorkerQueueMetaStore.class);
        Mockito.when(workerGroupExecutorInterface.listAllWorkerQueueIds()).thenReturn(
            Set.of(TEST_CONSUMER_GROUP_NAME, NO_LAG_TEST_WORKER_GROUP_NAME)
        );

        return workerGroupExecutorInterface;
    }
}
