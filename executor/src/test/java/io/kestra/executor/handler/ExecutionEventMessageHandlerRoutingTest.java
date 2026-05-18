package io.kestra.executor.handler;

import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.WorkerGroup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ExecutionEventMessageHandlerRoutingTest {

    private Task aSystemTask() {
        Task task = mock(Task.class, withSettings().extraInterfaces(SystemTask.class));
        when(task.getType()).thenReturn("io.kestra.test.SystemTask");
        return task;
    }

    private Task aRegularTask() {
        Task task = mock(Task.class);
        when(task.getType()).thenReturn("io.kestra.test.RegularTask");
        return task;
    }

    @Test
    void shouldRouteSystemTaskToSystemKeyWhenNoUserGroup() {
        String resolved = ExecutionEventMessageHandler.resolveRoutingKey(aSystemTask(), null);
        assertThat(resolved).isEqualTo(WorkerGroup.SYSTEM_KEY);
    }

    @Test
    void shouldOverrideUserWorkerGroupForSystemTask() {
        String resolved = ExecutionEventMessageHandler.resolveRoutingKey(aSystemTask(), "user-group");
        assertThat(resolved).isEqualTo(WorkerGroup.SYSTEM_KEY);
    }

    @Test
    void shouldAcceptSystemKeyExplicitlyOnSystemTask() {
        String resolved = ExecutionEventMessageHandler.resolveRoutingKey(aSystemTask(), WorkerGroup.SYSTEM_KEY);
        assertThat(resolved).isEqualTo(WorkerGroup.SYSTEM_KEY);
    }

    @Test
    void shouldPassThroughSystemWorkerGroupForRegularTask() {
        String resolved = ExecutionEventMessageHandler.resolveRoutingKey(aRegularTask(), WorkerGroup.SYSTEM_KEY);
        assertThat(resolved).isEqualTo(WorkerGroup.SYSTEM_KEY);
    }

    @Test
    void shouldPassThroughUserWorkerGroupForRegularTask() {
        String resolved = ExecutionEventMessageHandler.resolveRoutingKey(aRegularTask(), "user-group");
        assertThat(resolved).isEqualTo("user-group");
    }

    @Test
    void shouldPassThroughNullForRegularTask() {
        String resolved = ExecutionEventMessageHandler.resolveRoutingKey(aRegularTask(), null);
        assertThat(resolved).isNull();
    }
}
