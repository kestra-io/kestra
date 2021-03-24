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
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Named;

@Slf4j
public class StandAloneRunner implements RunnerInterface, Closeable {
    @Setter private ExecutorService poolExecutor;
    @Setter protected int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    @Setter protected int executorThreads = 1;
    @Setter protected int indexerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    @Setter protected int schedulerThread = 1; //TODO scale

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASK_NAMED)
    protected QueueInterface<WorkerTask> workerTaskQueue;

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

        for (int i = 0; i < executorThreads; i++) {
            poolExecutor.execute(applicationContext.getBean(AbstractExecutor.class));
        }

        Worker worker = new Worker(applicationContext, workerThread);
        applicationContext.registerSingleton(worker);
        poolExecutor.execute(worker);

        for (int i = 0; i < schedulerThread; i++) {
            poolExecutor.execute(applicationContext.getBean(AbstractScheduler.class));
        }

        if (applicationContext.containsBean(IndexerInterface.class)) {
            for (int i = 0; i < indexerThread; i++) {
                poolExecutor.execute(applicationContext.getBean(IndexerInterface.class));
            }
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
