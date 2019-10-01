package org.floworc.core.runners;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class StandAloneRunner implements RunnerInterface, Closeable {
    private ExecutorService poolExecutor = Executors.newCachedThreadPool();

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

    @Inject
    private FlowRepositoryInterface flowRepository;

    private boolean running = false;

    @Override
    public void run() {
        this.running = true;

        int processors = Math.max(3, Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < processors; i++) {
            poolExecutor.execute(applicationContext.getBean(Executor.class));

            poolExecutor.execute(applicationContext.getBean(ExecutionStateInterface.class));

            poolExecutor.execute(applicationContext.getBean(Worker.class));
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
