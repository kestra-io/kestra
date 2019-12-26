package org.kestra.core.runners;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class StandAloneRunner implements RunnerInterface, Closeable {
    private ExecutorService poolExecutor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("standalone-runner-%d").build()
    );
    private int threads = Math.max(3, Runtime.getRuntime().availableProcessors());

    public void setThreads(int threads) {
        this.threads = threads;
    }

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

        for (int i = 0; i < this.threads; i++) {
            poolExecutor.execute(applicationContext.getBean(AbstractExecutor.class));

            poolExecutor.execute(applicationContext.getBean(Worker.class));

            if (applicationContext.containsBean(Indexer.class)) {
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
