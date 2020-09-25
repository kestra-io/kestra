package org.kestra.core.runners;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionKilled;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.tasks.scripts.Bash;
import org.kestra.core.utils.Await;
import org.kestra.core.utils.TestsUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class WorkerTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASK_NAMED)
    QueueInterface<WorkerTask> workerTaskQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    QueueInterface<ExecutionKilled> executionKilledQueue;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void success() throws TimeoutException {
        Worker worker = new Worker(applicationContext, 8);
        worker.run();

        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>(null);
        workerTaskResultQueue.receive(workerTaskResult::set);

        workerTaskQueue.emit(workerTask("sleep 1"));

        Await.until(
            () -> workerTaskResult.get() != null && workerTaskResult.get().getTaskRun().getState().isTerninated(),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );

        assertThat(workerTaskResult.get().getTaskRun().getState().getHistories().size(), is(3));
    }

    @Test
    void killed() throws InterruptedException, TimeoutException {
        Worker worker = new Worker(applicationContext, 8);
        worker.run();

        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>(null);
        workerTaskResultQueue.receive(workerTaskResult::set);

        WorkerTask workerTask = workerTask("sleep infinity");

        workerTaskQueue.emit(workerTask);
        Thread.sleep(500);

        executionKilledQueue.emit(ExecutionKilled.builder().executionId(workerTask.getTaskRun().getExecutionId()).build());

        Await.until(
            () -> workerTaskResult.get() != null && workerTaskResult.get().getTaskRun().getState().isTerninated(),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );

        assertThat(workerTaskResult.get().getTaskRun().getState().getHistories().size(), is(3));
        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent(), is(State.Type.KILLED));
    }

    private WorkerTask workerTask(String command) {
        Bash bash = Bash.builder()
            .type(Bash.class.getName())
            .id("unit-test")
            .commands(new String[]{command})
            .build();

        Flow flow = Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unit-test")
            .tasks(Collections.singletonList(bash))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ResolvedTask resolvedTask = ResolvedTask.of(bash);

        return WorkerTask.builder()
            .runContext(runContextFactory.of(ImmutableMap.of("key", "value")))
            .task(bash)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }
}
