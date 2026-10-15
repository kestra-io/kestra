package io.kestra.worker;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.fetchers.WorkerJobFetcher;
import io.kestra.worker.processors.WorkerJobProcessor;
import io.kestra.worker.processors.WorkerJobProcessorFactory;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the consumer-side deadline: a job that exceeds it is reported and its thread is written off,
 * but the consumer that owned that thread must not go back to polling, or it would pull jobs the pool
 * has no thread left to run.
 */
@KestraTest
class WorkerJobExecutorTimeoutTest {
    private static final Duration TIMEOUT = Duration.ofMillis(200);
    private static final String HANGING = "hanging-job";

    @Inject
    private WorkerQueueRegistry workerQueueRegistry;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private MetricRegistry metricRegistry;

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger processCount = new AtomicInteger();
    private final AtomicInteger timeoutCount = new AtomicInteger();

    private WorkerJobExecutor executor;

    @AfterEach
    void tearDown() {
        release.countDown();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldStopTakingJobsWhenOneExceedsItsTimeout() {
        // Given a single-threaded worker whose only thread is taken by a job that never returns
        WorkerQueue<WorkerJob> queue = start(1);
        queue.put(job(HANGING));
        await().atMost(Duration.ofSeconds(10)).until(() -> processCount.get() == 1);

        // When the deadline passes
        await().atMost(Duration.ofSeconds(10)).until(() -> timeoutCount.get() == 1);

        // Then the next job stays in the queue rather than being submitted behind the held thread
        queue.put(job("next-job"));
        await()
            .during(TIMEOUT.multipliedBy(2))
            .atMost(Duration.ofSeconds(10))
            .until(() -> processCount.get() == 1);
        assertThat(timeoutCount.get()).isEqualTo(1);

        // And the consumer picks it up once the thread is finally free
        release.countDown();
        await().atMost(Duration.ofSeconds(10)).until(() -> processCount.get() == 2);
        assertThat(timeoutCount.get()).isEqualTo(1);
    }

    private WorkerQueue<WorkerJob> start(int workerThreads) {
        WorkerJobProcessorFactory factory = mock(WorkerJobProcessorFactory.class);
        when(factory.create(any(), any())).thenReturn(processor());

        WorkerContext context = new WorkerContext(IdUtils.create(), WorkerGroups.DEFAULT_ID, workerThreads);
        executor = new WorkerJobExecutor(
            workerQueueRegistry,
            executorsUtils,
            factory,
            metricRegistry,
            mock(WorkerJobFetcher.class)
        );
        WorkerQueue<WorkerJob> queue = workerQueueRegistry.getOrCreate(context, WorkerJob.class);
        executor.start(context);
        return queue;
    }

    private WorkerJobProcessor<WorkerJob> processor() {
        return new WorkerJobProcessor<>() {
            @Override
            public void process(WorkerJob workerJob) {
                processCount.incrementAndGet();
                if (HANGING.equals(workerJob.uid())) {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public Duration timeout(WorkerJob workerJob) {
                return TIMEOUT;
            }

            @Override
            public void onTimeout(WorkerJob workerJob) {
                timeoutCount.incrementAndGet();
            }

            @Override
            public void stop() {
            }

            @Override
            public void kill() {
            }

            @Override
            public void signalShutdownInterrupt() {
            }
        };
    }

    private WorkerJob job(String uid) {
        WorkerJob job = mock(WorkerJob.class);
        when(job.uid()).thenReturn(uid);
        return job;
    }
}
