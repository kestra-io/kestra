package org.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;

import javax.inject.Inject;
import javax.inject.Named;

@Factory
public class RunnerProcessFactory {
    @Prototype
    @Inject
    public Worker worker(
        ApplicationContext applicationContext,
        @Named(QueueFactoryInterface.WORKERTASK_NAMED) QueueInterface<WorkerTask> workerTaskQueue,
        @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED) QueueInterface<WorkerTaskResult> workerTaskResultQueue,
        MetricRegistry metricRegistry
    ) {
        return new Worker(
            applicationContext,
            workerTaskQueue,
            workerTaskResultQueue,
            metricRegistry
        );
    }
}
