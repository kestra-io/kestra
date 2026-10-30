package io.kestra.worker.queues;

import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.worker.models.WorkerContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkerQueueRegistryTest {

    @Test
    void shouldSizeQueuesFromThreadsWhenBufferIsSmallerThanThreads() {
        WorkerQueueRegistry registry = new WorkerQueueRegistry(mock(MetricRegistry.class));
        WorkerContext context = new WorkerContext("worker-1", "group-1", 8, 0);

        WorkerQueue<WorkerJob> jobQueue = registry.getOrCreate(context, WorkerJob.class);
        WorkerQueue<LogEntry> logQueue = registry.getOrCreate(context, LogEntry.class);

        assertThat(jobQueue.capacity()).isEqualTo(8);
        assertThat(logQueue.capacity()).isEqualTo(32);
    }
}
