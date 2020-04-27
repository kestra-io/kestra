package org.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.utils.ThreadMainFactoryBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Named;

@Slf4j
public class StandAloneRunner implements RunnerInterface, Closeable {
    private ExecutorService poolExecutor;
    protected int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    protected int executorThreads = Math.max(3, Runtime.getRuntime().availableProcessors());
    protected int indexerThread = Math.max(3, Runtime.getRuntime().availableProcessors());

    public void setWorkerThread(int workerThread) {
        this.workerThread = workerThread;
    }

    public void setExecutorThreads(int threads) {
        this.executorThreads = threads;
    }

    public void setIndexerThread(int indexerThread) {
        this.indexerThread = indexerThread;
    }

    @Inject
    private ThreadMainFactoryBuilder threadFactoryBuilder;

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

        poolExecutor = Executors.newCachedThreadPool(threadFactoryBuilder.build("standalone-runner-%d"));

        for (int i = 0; i < executorThreads; i++) {
            poolExecutor.execute(applicationContext.getBean(AbstractExecutor.class));
        }

        for (int i = 0; i < workerThread; i++) {
            poolExecutor.execute(applicationContext.getBean(Worker.class));
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
