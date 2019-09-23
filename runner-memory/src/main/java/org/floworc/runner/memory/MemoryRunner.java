package org.floworc.runner.memory;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.runners.StandAloneRunner;
import org.floworc.core.runners.WorkerTask;
import org.floworc.core.runners.WorkerTaskResult;

import javax.inject.Singleton;

@Slf4j
@Singleton
public class MemoryRunner extends StandAloneRunner {
    @Override
    public void run() {
        super.run();

        int processors = Math.max(3, Runtime.getRuntime().availableProcessors());

        // @FIXME: Ugly hack to wait that all thread is created and ready to listen
        boolean isReady;
        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("Can't sleep", e);
            }

            isReady = ((MemoryQueue<Execution>) this.executionQueue).getSubscribersCount() == processors * 2 &&
                ((MemoryQueue<WorkerTask>) this.workerTaskQueue).getSubscribersCount() == processors &&
                ((MemoryQueue<WorkerTaskResult>) this.workerTaskResultQueue).getSubscribersCount() == processors;
        }
        while (!isReady);
    }
}
