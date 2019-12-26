package org.kestra.runner.memory;

import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.runners.StandAloneRunner;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.utils.Await;

import javax.inject.Singleton;

@Slf4j
@Singleton
public class MemoryRunner extends StandAloneRunner {
    @Override
    public void run() {
        super.run();

        int processors = Math.max(3, Runtime.getRuntime().availableProcessors());

        // @FIXME: Ugly hack to wait that all threads is created and ready to listen
        Await.until(() -> ((MemoryQueue<Execution>) this.executionQueue).getSubscribersCount() == processors * 2 &&
            ((MemoryQueue<WorkerTask>) this.workerTaskQueue).getSubscribersCount() == processors &&
            ((MemoryQueue<WorkerTaskResult>) this.workerTaskResultQueue).getSubscribersCount() == processors
        );
    }
}
