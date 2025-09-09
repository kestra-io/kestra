package io.kestra.core.queues;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.Either;

import java.util.function.Consumer;

public interface WorkerJobQueueInterface extends QueueInterface<WorkerJob> {
    
    Runnable subscribe(String workerId, String workerGroup, Consumer<Either<WorkerJob, DeserializationException>> consumer);
}
