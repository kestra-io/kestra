package io.kestra.worker.processors.internals;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskData;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.worker.WorkerGroups;

import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@KestraTest
class WorkerTaskCallableTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private MetricRegistry metricRegistry;

    @Test
    void shouldFailAndInvokeKillWhenTaskExceedsTimeout() {
        // Given: a task that ignores interrupt and only returns after kill(), with a short timeout
        HangUntilKilledTask task = HangUntilKilledTask.builder()
            .id("hang-task")
            .type(HangUntilKilledTask.class.getName())
            .timeout(Property.ofValue(Duration.ofMillis(200)))
            .build();
        WorkerTaskCallable callable = callable(task);

        // When
        State.Type state = assertTimeout(Duration.ofSeconds(5), callable::call);

        // Then: timeout is reported as FAILED with TimeoutExceededException, and kill() ran
        // while the task was still blocked so the worker slot is released.
        assertThat(state).isEqualTo(State.Type.FAILED);
        assertThat(callable.getException()).isInstanceOf(TimeoutExceededException.class);
        assertThat(task.wasKilled()).isTrue();
    }

    private WorkerTaskCallable callable(HangUntilKilledTask task) {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(List.of(task))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        ResolvedTask resolvedTask = ResolvedTask.of(task);
        RunContext runContext = runContextFactory.of(Map.of("key", "value"));

        WorkerTask workerTask = WorkerTask.builder()
            .data(WorkerTaskData.from(runContext))
            .task(task)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();

        return new WorkerTaskCallable(workerTask, task, runContext, metricRegistry, WorkerGroups.DEFAULT_ID);
    }

    /**
     * Task whose {@code run()} ignores interrupt and only returns after {@link #kill()}.
     * Proves the timeout watchdog invokes {@code task.kill()} instead of waiting forever.
     */
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class HangUntilKilledTask extends Task implements RunnableTask<VoidOutput> {
        @Builder.Default
        private final AtomicBoolean killed = new AtomicBoolean(false);

        @Override
        public VoidOutput run(RunContext runContext) {
            while (!killed.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    // Swallow interrupt — only kill() unblocks this task.
                }
            }
            return null;
        }

        @Override
        public void kill() {
            killed.set(true);
        }

        public boolean wasKilled() {
            return killed.get();
        }
    }
}
