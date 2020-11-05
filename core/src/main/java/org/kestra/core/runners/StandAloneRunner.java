package org.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.schedulers.Scheduler;
import org.kestra.core.utils.ExecutorsUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Named;

@Slf4j
public class StandAloneRunner implements RunnerInterface, Closeable {
    private ExecutorService poolExecutor;
    protected final int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    protected final int executorThreads = 1;
    protected final int indexerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    protected final int schedulerThread = 1; //TODO scale

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

        poolExecutor.execute(new Worker(applicationContext, workerThread));

        for (int i = 0; i < schedulerThread; i++) {
            poolExecutor.execute(applicationContext.getBean(Scheduler.class));
        }

        if (applicationContext.containsBean(Indexer.class)) {
            for (int i = 0; i < indexerThread; i++) {
                poolExecutor.execute(applicationContext.getBean(Indexer.class));
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
