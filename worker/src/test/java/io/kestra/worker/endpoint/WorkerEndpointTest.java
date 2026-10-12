package io.kestra.worker.endpoint;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerEndpointTest {

    @Mock
    private Worker worker;

    @InjectMocks
    private WorkerEndpoint workerEndpoint;

    @Test
    void shouldReturnEmptyResultWhenNoRunningJobs() throws Exception {
        when(worker.getRunningJobs()).thenReturn(List.of());

        WorkerEndpoint.WorkerEndpointResult result = workerEndpoint.running();

        assertThat(result.getRunningCount()).isEqualTo(0);
        assertThat(result.getRunnings()).isEmpty();
    }

    @Test
    void shouldReturnCorrectResultForWorkerTasks() throws Exception {
        TaskRun taskRun = mock(TaskRun.class);
        Task task = mock(Task.class);
        
        WorkerTask workerTask = mock(WorkerTask.class);
        when(workerTask.getType()).thenReturn("task");
        when(workerTask.getTaskRun()).thenReturn(taskRun);
        when(workerTask.getTask()).thenReturn(task);
        
        when(worker.getRunningJobs()).thenReturn(List.of(workerTask));

        WorkerEndpoint.WorkerEndpointResult result = workerEndpoint.running();

        assertThat(result.getRunningCount()).isEqualTo(1);
        assertThat(result.getRunnings()).hasSize(1);
        
        WorkerEndpoint.WorkerEndpointWorkerTask endpointTask = result.getRunnings().getFirst();
        assertThat(endpointTask.getType()).isEqualTo("task");
        assertThat(endpointTask.getTaskRun()).isEqualTo(taskRun);
        assertThat(endpointTask.getTask()).isEqualTo(task);
        assertThat(endpointTask.getTrigger()).isNull();
    }

    @Test
    void shouldReturnCorrectResultForWorkerTriggers() throws Exception {
        AbstractTrigger trigger = mock(AbstractTrigger.class);
        
        WorkerTrigger workerTrigger = mock(WorkerTrigger.class);
        when(workerTrigger.getType()).thenReturn("trigger");
        when(workerTrigger.getTrigger()).thenReturn(trigger);
        
        when(worker.getRunningJobs()).thenReturn(List.of(workerTrigger));

        WorkerEndpoint.WorkerEndpointResult result = workerEndpoint.running();

        assertThat(result.getRunningCount()).isEqualTo(0);
        assertThat(result.getRunnings()).hasSize(1);
        
        WorkerEndpoint.WorkerEndpointWorkerTask endpointTask = result.getRunnings().getFirst();
        assertThat(endpointTask.getType()).isEqualTo("trigger");
        assertThat(endpointTask.getTaskRun()).isNull();
        assertThat(endpointTask.getTask()).isNull();
        assertThat(endpointTask.getTrigger()).isEqualTo(trigger);
    }

    @Test
    void shouldReturnCorrectResultForMixedJobs() throws Exception {
        WorkerTask workerTask = mock(WorkerTask.class);
        when(workerTask.getType()).thenReturn("task");
        
        WorkerTrigger workerTrigger = mock(WorkerTrigger.class);
        when(workerTrigger.getType()).thenReturn("trigger");
        
        when(worker.getRunningJobs()).thenReturn(List.of(workerTask, workerTrigger));

        WorkerEndpoint.WorkerEndpointResult result = workerEndpoint.running();

        assertThat(result.getRunningCount()).isEqualTo(1);
        assertThat(result.getRunnings()).hasSize(2);
    }
}