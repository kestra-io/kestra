package io.kestra.runner.memory;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.Await;

import java.time.Duration;
import jakarta.inject.Singleton;

@Slf4j
@Singleton
public class MemoryRunner extends StandAloneRunner {
    @SneakyThrows
    @Override
    public void run() {
        super.run();

        // @FIXME: Ugly hack to wait that all threads is created and ready to listen
        Await.until(
            () -> ((MemoryQueue<Execution>) this.executionQueue).getSubscribersCount() == 1 + 1 + 1 && // executorThreads + indexerThread + schedulerThread &&
                ((MemoryQueue<WorkerJob>) this.workerTaskQueue).getSubscribersCount() == 1 &&
                ((MemoryQueue<WorkerTaskResult>) this.workerTaskResultQueue).getSubscribersCount() == 1, // executorThreads,
            null,
            Duration.ofSeconds(5)
        );
    }
}
