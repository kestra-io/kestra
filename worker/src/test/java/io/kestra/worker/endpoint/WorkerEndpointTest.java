package io.kestra.worker.endpoint;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTrigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerEndpointTest {
    private final Worker worker = mock(Worker.class);
    private final WorkerEndpoint endpoint = new WorkerEndpoint();

    WorkerEndpointTest() {
        endpoint.worker = worker;
    }

    @Test
    void shouldReturnEmptyResultWhenNoJobsAreRunning() throws Exception {
        when(worker.getRunningJobs()).thenReturn(List.of());

        WorkerEndpoint.WorkerEndpointResult result = endpoint.running();

        assertThat(result.getRunningCount()).isZero();
        assertThat(result.getRunnings()).isEmpty();
    }

    @Test
    void shouldCountTasksAndMapRunningJobs() throws Exception {
        TaskRun taskRun = mock(TaskRun.class);
        Task task = mock(Task.class);
        AbstractTrigger trigger = mock(AbstractTrigger.class);
        WorkerTask workerTask = mock(WorkerTask.class);
        WorkerTrigger workerTrigger = mock(WorkerTrigger.class);
        when(workerTask.getType()).thenReturn(WorkerTask.TYPE);
        when(workerTask.getTaskRun()).thenReturn(taskRun);
        when(workerTask.getTask()).thenReturn(task);
        when(workerTrigger.getType()).thenReturn(WorkerTrigger.TYPE);
        when(workerTrigger.getTrigger()).thenReturn(trigger);
        when(worker.getRunningJobs()).thenReturn(List.of(workerTask, workerTrigger));

        WorkerEndpoint.WorkerEndpointResult result = endpoint.running();

        assertThat(result.getRunningCount()).isEqualTo(1);
        assertThat(result.getRunnings())
            .extracting(
                WorkerEndpoint.WorkerEndpointWorkerTask::getType,
                WorkerEndpoint.WorkerEndpointWorkerTask::getTaskRun,
                WorkerEndpoint.WorkerEndpointWorkerTask::getTask,
                WorkerEndpoint.WorkerEndpointWorkerTask::getTrigger
            )
            .containsExactly(
                tuple(WorkerTask.TYPE, taskRun, task, null),
                tuple(WorkerTrigger.TYPE, null, null, trigger)
            );
    }

    @Test
    void shouldNotCountTriggersAsRunningTasks() throws Exception {
        WorkerTrigger workerTrigger = mock(WorkerTrigger.class);
        when(workerTrigger.getType()).thenReturn(WorkerTrigger.TYPE);
        when(workerTrigger.getTrigger()).thenReturn(mock(AbstractTrigger.class));
        when(worker.getRunningJobs()).thenReturn(List.of(workerTrigger));

        WorkerEndpoint.WorkerEndpointResult result = endpoint.running();

        assertThat(result.getRunningCount()).isZero();
        assertThat(result.getRunnings()).hasSize(1);
        assertThat(result.getRunnings().getFirst().getTrigger()).isSameAs(workerTrigger.getTrigger());
    }
}
