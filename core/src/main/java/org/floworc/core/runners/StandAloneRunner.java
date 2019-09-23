package org.floworc.core.runners;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.queues.QueueInterface;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class StandAloneRunner implements RunnerInterface {
    private ExecutorService poolExecutor = Executors.newCachedThreadPool();

    @Inject
    @Named("executionQueue")
    protected QueueInterface<Execution> executionQueue;

    @Inject
    @Named("workerTaskQueue")
    protected QueueInterface<WorkerTask> workerTaskQueue;

    @Inject
    @Named("workerTaskResultQueue")
    protected QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    private ApplicationContext applicationContext;

    @Override
    public void run() {
        int processors = Math.max(3, Runtime.getRuntime().availableProcessors());

        for (int i = 0; i < processors; i++) {
            poolExecutor.execute(applicationContext.getBean(Executor.class));

            poolExecutor.execute(applicationContext.getBean(ExecutionStateInterface.class));

            poolExecutor.execute(applicationContext.getBean(Worker.class));
        }
    }

    public Execution runOne(Flow flow) throws InterruptedException, IOException {
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

        this.executionQueue.close();
        this.workerTaskQueue.close();
        this.workerTaskResultQueue.close();

        return receive.get();
    }
}
