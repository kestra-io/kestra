package org.floworc.core.runners.types;

import com.devskiller.friendly_id.FriendlyId;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.runners.ExecutionService;
import org.floworc.core.runners.WorkerTask;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.queues.QueueMessage;
import org.floworc.core.queues.QueueName;
import org.floworc.core.queues.types.LocalQueue;
import org.floworc.core.repositories.types.LocalRepository;
import org.floworc.core.runners.ExecutionState;
import org.floworc.core.runners.Executor;
import org.floworc.core.runners.RunnerInterface;
import org.floworc.core.runners.Worker;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class StandAloneRunner implements RunnerInterface {
    private LocalQueue<Execution> executionQueue;
    private LocalQueue<WorkerTask> workerTaskQueue;
    private LocalQueue<WorkerTask> workerTaskResultQueue;
    private LocalRepository localRepository;
    private ThreadPoolExecutor poolExecutor;
    private ExecutionService executionService;

    public StandAloneRunner(File basePath) {
        this.executionQueue = new LocalQueue<>(QueueName.EXECUTIONS);
        this.workerTaskQueue = new LocalQueue<>(QueueName.WORKERS);
        this.workerTaskResultQueue = new LocalQueue<>(QueueName.WORKERS_RESULT);

        this.localRepository = new LocalRepository(basePath);
        this.executionService = new ExecutionService(this.workerTaskResultQueue);
    }

    @Override
    public void run() {
        poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
        poolExecutor.execute(new Executor(
            this.executionQueue,
            this.workerTaskQueue,
            this.localRepository,
            this.executionService
        ));

        poolExecutor.execute(new ExecutionState(
            this.executionQueue,
            this.workerTaskResultQueue
        ));

        while(poolExecutor.getActiveCount() != poolExecutor.getCorePoolSize()) {
            poolExecutor.execute(new Worker(
                this.workerTaskQueue,
                this.workerTaskResultQueue
            ));
        }
    }


    public Execution run(Flow flow) throws InterruptedException {
        this.run();

        Execution execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .flowId(flow.getId())
            .state(new State())
            .build();

        AtomicReference<Execution> receive = new AtomicReference<>();

        this.executionQueue.receive(message -> {
            if (message.getBody().getState().isTerninated()) {
                receive.set(message.getBody());

                this.poolExecutor.shutdownNow();
            }
        });

        this.executionQueue.emit(
            QueueMessage.<Execution>builder()
                .key(execution.getId())
                .body(execution)
                .build()
        );

        this.poolExecutor.awaitTermination(1, TimeUnit.MINUTES);

        return receive.get();
    }

}
