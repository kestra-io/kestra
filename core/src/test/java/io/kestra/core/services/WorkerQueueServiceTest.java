package io.kestra.core.services;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.NoMatchingWorkerQueueException;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.runners.WorkerQueueRouting;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerData;
import io.kestra.core.worker.WorkerQueues;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.log.PurgeLogs;
import io.kestra.plugin.core.trigger.Schedule;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerQueueServiceTest {

    private final WorkerQueueService service = new WorkerQueueService.Default();

    @Test
    void shouldRouteSystemTaskToSystemQueueWhenWorkerJobIsSystemTask() throws NoMatchingWorkerQueueException {
        WorkerTask workerTask = WorkerTask.builder()
            .task(PurgeLogs.builder().id("purge").type(PurgeLogs.class.getName()).build())
            .taskRun(TaskRun.builder().build())
            .build();

        Optional<WorkerQueueRouting> result = service.resolveWorkerQueueForJob(buildFlow(), workerTask);

        assertThat(result).isPresent();
        assertThat(result.get().isSystem()).isTrue();
        assertThat(result.get().workerQueueId()).isEqualTo(WorkerQueues.SYSTEM_ID);
    }

    @Test
    void shouldRouteSystemTaskToSystemQueueWhenSystemTaskCarriesWorkerSelectorTags() throws NoMatchingWorkerQueueException {
        WorkerTask workerTask = WorkerTask.builder()
            .task(PurgeLogs.builder()
                .id("purge")
                .type(PurgeLogs.class.getName())
                .workerSelector(new WorkerSelector(List.of("docker"), null))
                .build())
            .taskRun(TaskRun.builder().build())
            .build();

        Optional<WorkerQueueRouting> result = service.resolveWorkerQueueForJob(buildFlow(), workerTask);

        assertThat(result).isPresent();
        assertThat(result.get().isSystem()).isTrue();
    }

    @Test
    void shouldDelegateToDoResolveWhenWorkerJobIsRegularTask() throws NoMatchingWorkerQueueException {
        WorkerTask workerTask = WorkerTask.builder()
            .task(Return.builder().id("return").build())
            .taskRun(TaskRun.builder().build())
            .build();

        // Noop returns empty for non-SystemTask jobs.
        assertThat(service.resolveWorkerQueueForJob(buildFlow(), workerTask)).isEmpty();
    }

    @Test
    void shouldDelegateToDoResolveWhenWorkerJobIsTrigger() throws NoMatchingWorkerQueueException {
        WorkerTrigger workerTrigger = WorkerTrigger.builder()
            .trigger(Schedule.builder().id("trigger").build())
            .data(new WorkerTriggerData(
                "tenant", "ns", "flow", null, null, null, null,
                Collections.emptyMap(), null, null, Collections.emptyMap()))
            .build();

        // Triggers cannot be SystemTasks, so the short-circuit does not apply.
        assertThat(service.resolveWorkerQueueForJob(buildFlow(), workerTrigger)).isEmpty();
    }

    private FlowInterface buildFlow() {
        return Flow.builder()
            .id("flow")
            .namespace("namespace")
            .tenantId("tenant")
            .build();
    }
}
