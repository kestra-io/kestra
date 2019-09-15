package org.floworc.core.runners.types;

import com.devskiller.friendly_id.FriendlyId;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.queues.types.LocalQueue;
import org.floworc.core.repositories.types.LocalFlowRepository;
import org.floworc.core.runners.*;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class StandAloneRunner implements RunnerInterface {
    private LocalQueue<Execution> executionQueue;
    private LocalQueue<WorkerTask> workerTaskQueue;
    private LocalQueue<WorkerTaskResult> workerTaskResultQueue;
    private LocalFlowRepository localRepository;
    private ThreadPoolExecutor poolExecutor;
    private ExecutionService executionService;

    public StandAloneRunner(File basePath) {
        this.executionQueue = new LocalQueue<>(Execution.class);
        this.workerTaskQueue = new LocalQueue<>(WorkerTask.class);
        this.workerTaskResultQueue = new LocalQueue<>(WorkerTaskResult.class);

        this.localRepository = new LocalFlowRepository(basePath);
        this.executionService = new ExecutionService(this.workerTaskResultQueue);
    }

    @Override
    public void run() {
        int processors = Math.max(3, Runtime.getRuntime().availableProcessors());
        poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(processors * 4);

        for (int i = 0; i < processors; i++) {
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

            poolExecutor.execute(new Worker(
                this.workerTaskQueue,
                this.workerTaskResultQueue
            ));
        }

        // @FIXME: Ugly hack to wait that all thread is created and ready to listen
        boolean isReady = false;
        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("Can't sleep", e);
            }

            isReady = this.executionQueue.getSubscribersCount() == processors * 2 &&
                this.workerTaskQueue.getSubscribersCount() == processors &&
                this.workerTaskResultQueue.getSubscribersCount() == processors;
        }
        while (!isReady);
    }

    public Execution run(Flow flow) throws InterruptedException {
        this.run();

        Execution execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .flowId(flow.getId())
            .state(new State())
            .build();

        AtomicReference<Execution> receive = new AtomicReference<>();

        this.executionQueue.receive(StandAloneRunner.class, current -> {
            if (current.getState().isTerninated()) {
                receive.set(current);

                poolExecutor.shutdown();
            }
        });

        this.executionQueue.emit(execution);

        poolExecutor.awaitTermination(1, TimeUnit.MINUTES);

        return receive.get();
    }

}
