package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import jakarta.inject.Singleton;

import java.util.Map;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class AfterExecutionTestCase {

    @SuppressWarnings("unchecked")
    public void shouldCallTasksAfterExecution(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);

        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getTaskId()).isEqualTo("mytask");
        assertThat(taskRun.getState().getCurrent()).isEqualTo(SUCCESS);

        TaskRun afterExecution = execution.getTaskRunList().getLast();
        assertThat(afterExecution.getTaskId()).isEqualTo("end");
        assertThat(afterExecution.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(afterExecution.getState().getStartDate()).isAfterOrEqualTo(taskRun.getState().getEndDate().orElseThrow());
        assertThat(afterExecution.getState().getStartDate()).isAfterOrEqualTo(execution.getState().getEndDate().orElseThrow());
        Map<String, Object> outputs = (Map<String, Object> ) afterExecution.getOutputs().get("values");
        assertThat(outputs.get("state")).isEqualTo("SUCCESS");
        // afterExecution should be able to access execution outputs
        assertThat(outputs.get("output")).isEqualTo("this is a task output used as a final flow output");
    }

    @SuppressWarnings("unchecked")
    public void shouldCallTasksAfterFinally(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);

        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getState().getCurrent()).isEqualTo(SUCCESS);

        TaskRun finallyTaskRun = execution.getTaskRunList().get(1);
        assertThat(finallyTaskRun.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(finallyTaskRun.getState().getStartDate()).isAfterOrEqualTo(taskRun.getState().getEndDate().orElseThrow());

        TaskRun afterExecution = execution.getTaskRunList().getLast();
        assertThat(afterExecution.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(afterExecution.getState().getStartDate()).isAfterOrEqualTo(finallyTaskRun.getState().getEndDate().orElseThrow());
        assertThat(afterExecution.getState().getStartDate()).isAfterOrEqualTo(execution.getState().getEndDate().orElseThrow());
        Map<String, Object> outputs = (Map<String, Object> ) afterExecution.getOutputs().get("values");
        assertThat(outputs.get("state")).isEqualTo("SUCCESS");
    }

    @SuppressWarnings("unchecked")
    public void shouldCallTasksAfterError(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(FAILED);
        assertThat(execution.getTaskRunList()).hasSize(3);

        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getState().getCurrent()).isEqualTo(FAILED);

        TaskRun errorTaskRun = execution.getTaskRunList().get(1);
        assertThat(errorTaskRun.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(errorTaskRun.getState().getStartDate()).isAfterOrEqualTo(taskRun.getState().getEndDate().orElseThrow());

        TaskRun afterExecution = execution.getTaskRunList().getLast();
        assertThat(afterExecution.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(afterExecution.getState().getStartDate()).isAfterOrEqualTo(taskRun.getState().getEndDate().orElseThrow());
        assertThat(afterExecution.getState().getStartDate()).isAfterOrEqualTo(execution.getState().getEndDate().orElseThrow());
        Map<String, Object> outputs = (Map<String, Object> ) afterExecution.getOutputs().get("values");
        assertThat(outputs.get("state")).isEqualTo("FAILED");
    }

    @SuppressWarnings("unchecked")
    public void shouldCallTasksAfterListener(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);

        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getState().getCurrent()).isEqualTo(SUCCESS);

        TaskRun listenerTaskRun = execution.getTaskRunList().get(1);
        assertThat(taskRun.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(listenerTaskRun.getState().getStartDate()).isAfterOrEqualTo(taskRun.getState().getEndDate().orElseThrow());

        TaskRun afterExecution = execution.getTaskRunList().getLast();
        assertThat(afterExecution.getState().getCurrent()).isEqualTo(SUCCESS);
        assertThat(afterExecution.getState().getStartDate()).isAfterOrEqualTo(listenerTaskRun.getState().getEndDate().orElseThrow());
        assertThat(afterExecution.getState().getStartDate()).isAfterOrEqualTo(execution.getState().getEndDate().orElseThrow());
        Map<String, Object> outputs = (Map<String, Object> ) afterExecution.getOutputs().get("values");
        assertThat(outputs.get("state")).isEqualTo("SUCCESS");
    }
}
