package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Slf4j
public class StandAloneRunner implements RunnerInterface, AutoCloseable {
    private java.util.concurrent.ExecutorService poolExecutor;
    @Setter protected int workerThread = Math.max(3, Runtime.getRuntime().availableProcessors());
    @Setter protected boolean schedulerEnabled = true;
    @Setter protected boolean workerEnabled = true;

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

    private final List<AutoCloseable> servers = new ArrayList<>();

    private boolean running = false;

    @Override
    public void run() {
        this.running = true;

        poolExecutor = executorsUtils.cachedThreadPool("standalone-runner");

        poolExecutor.execute(applicationContext.getBean(ExecutorInterface.class));

        if (workerEnabled) {
            Worker worker = new Worker(applicationContext, workerThread, null);
            applicationContext.registerSingleton(worker);
            poolExecutor.execute(worker);
            servers.add(worker);
        }

        if (schedulerEnabled) {
            AbstractScheduler scheduler = applicationContext.getBean(AbstractScheduler.class);
            poolExecutor.execute(scheduler);
            servers.add(scheduler);
        }

        if (applicationContext.containsBean(IndexerInterface.class)) {
            IndexerInterface indexer = applicationContext.getBean(IndexerInterface.class);
            poolExecutor.execute(indexer);
            servers.add(indexer);
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    @Override
    public void close() throws Exception {
        this.servers.forEach(throwConsumer(AutoCloseable::close));
        this.poolExecutor.shutdown();
        this.executionQueue.close();
        this.workerTaskQueue.close();
        this.workerTaskResultQueue.close();
    }
}
