package org.floworc.core.queues;

import org.floworc.core.models.executions.Execution;
import org.floworc.core.runners.WorkerTask;
import org.floworc.core.runners.WorkerTaskResult;

public interface QueueFactoryInterface {
    QueueInterface<Execution> execution();

    QueueInterface<WorkerTask> workerTask();

    QueueInterface<WorkerTaskResult> workerTaskResult();
}
