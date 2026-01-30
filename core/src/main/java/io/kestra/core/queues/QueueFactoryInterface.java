package io.kestra.core.queues;

import io.kestra.core.runners.*;

public interface QueueFactoryInterface {
    String WORKERJOB_NAMED = "workerJobQueue";
    String WORKERTASKRESULT_NAMED = "workerTaskResultQueue";
    String WORKERTRIGGERRESULT_NAMED = "workerTriggerResultQueue";
    String WORKERJOBRUNNING_NAMED = "workerJobRunningQueue";

    WorkerJobQueueInterface workerJob();

    QueueInterface<WorkerTaskResult> workerTaskResult();

    QueueInterface<WorkerTriggerResult> workerTriggerResult();

    QueueInterface<WorkerJobRunning> workerJobRunning();
}
