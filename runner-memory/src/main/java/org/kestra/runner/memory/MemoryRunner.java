package org.kestra.runner.memory;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.runners.StandAloneRunner;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.utils.Await;

import java.time.Duration;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class MemoryRunner extends StandAloneRunner {
    @SneakyThrows
    @Override
    public void run() {
        super.run();

        // @FIXME: Ugly hack to wait that all threads is created and ready to listen
        Await.until(
            () -> ((MemoryQueue<Execution>) this.executionQueue).getSubscribersCount() == executorThreads + indexerThread &&
                ((MemoryQueue<WorkerTask>) this.workerTaskQueue).getSubscribersCount() == workerThread &&
                ((MemoryQueue<WorkerTaskResult>) this.workerTaskResultQueue).getSubscribersCount() == executorThreads,
            null,
            Duration.ofSeconds(5)
        );
    }
}
