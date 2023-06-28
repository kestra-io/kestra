package io.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.utils.ExecutorsUtils;

import java.io.Closeable;
import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@Slf4j
public class StandAloneRunner implements RunnerInterface, Closeable {
    @Setter private java.util.concurrent.ExecutorService poolExecutor;
    @Setter protected int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    @Setter protected boolean schedulerEnabled = true;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    protected QueueInterface<WorkerJob> workerTaskQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    protected QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    private ApplicationContext applicationContext;

    private boolean running = false;

    @Override
    public void run() {
        this.running = true;

        poolExecutor = executorsUtils.cachedThreadPool("standalone-runner");

        poolExecutor.execute(applicationContext.getBean(ExecutorInterface.class));

        Worker worker = new Worker(applicationContext, workerThread, null);
        applicationContext.registerSingleton(worker);
        poolExecutor.execute(worker);

        if (schedulerEnabled) {
            poolExecutor.execute(applicationContext.getBean(AbstractScheduler.class));
        }

        if (applicationContext.containsBean(IndexerInterface.class)) {
            poolExecutor.execute(applicationContext.getBean(IndexerInterface.class));
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    @Override
    public void close() throws IOException {
        this.poolExecutor.shutdown();
        this.executionQueue.close();
        this.workerTaskQueue.close();
        this.workerTaskResultQueue.close();
    }
}
