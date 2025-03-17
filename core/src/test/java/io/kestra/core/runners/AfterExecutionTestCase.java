package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import jakarta.inject.Singleton;

import java.util.Map;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;
import static org.exparity.hamcrest.date.InstantMatchers.sameOrAfter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@Singleton
public class AfterExecutionTestCase {

    @SuppressWarnings("unchecked")
    public void shouldCallTasksAfterExecution(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(2));

        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getTaskId(), is("hello"));
        assertThat(taskRun.getState().getCurrent(), is(SUCCESS));

        TaskRun afterExecution = execution.getTaskRunList().getLast();
        assertThat(afterExecution.getTaskId(), is("end"));
        assertThat(afterExecution.getState().getCurrent(), is(SUCCESS));
        assertThat(afterExecution.getState().getStartDate(), sameOrAfter(taskRun.getState().getEndDate().orElseThrow()));
        assertThat(afterExecution.getState().getStartDate(), sameOrAfter(execution.getState().getEndDate().orElseThrow()));
        Map<String, Object> outputs = (Map<String, Object> ) afterExecution.getOutputs().get("values");
        assertThat(outputs.get("state"), is("SUCCESS"));
    }

    @SuppressWarnings("unchecked")
    public void shouldCallTasksAfterFinally(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(3));

        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getState().getCurrent(), is(SUCCESS));

        TaskRun finallyTaskRun = execution.getTaskRunList().get(1);
        assertThat(finallyTaskRun.getState().getCurrent(), is(SUCCESS));
        assertThat(finallyTaskRun.getState().getStartDate(), sameOrAfter(taskRun.getState().getEndDate().orElseThrow()));

        TaskRun afterExecution = execution.getTaskRunList().getLast();
        assertThat(afterExecution.getState().getCurrent(), is(SUCCESS));
        assertThat(afterExecution.getState().getStartDate(), sameOrAfter(finallyTaskRun.getState().getEndDate().orElseThrow()));
        assertThat(afterExecution.getState().getStartDate(), sameOrAfter(execution.getState().getEndDate().orElseThrow()));
        Map<String, Object> outputs = (Map<String, Object> ) afterExecution.getOutputs().get("values");
        assertThat(outputs.get("state"), is("SUCCESS"));
    }

    @SuppressWarnings("unchecked")
    public void shouldCallTasksAfterError(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(FAILED));
        assertThat(execution.getTaskRunList(), hasSize(3));

        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getState().getCurrent(), is(FAILED));

        TaskRun errorTaskRun = execution.getTaskRunList().get(1);
        assertThat(errorTaskRun.getState().getCurrent(), is(SUCCESS));
        assertThat(errorTaskRun.getState().getStartDate(), sameOrAfter(taskRun.getState().getEndDate().orElseThrow()));

        TaskRun afterExecution = execution.getTaskRunList().getLast();
        assertThat(afterExecution.getState().getCurrent(), is(SUCCESS));
        assertThat(afterExecution.getState().getStartDate(), sameOrAfter(taskRun.getState().getEndDate().orElseThrow()));
        assertThat(afterExecution.getState().getStartDate(), sameOrAfter(execution.getState().getEndDate().orElseThrow()));
        Map<String, Object> outputs = (Map<String, Object> ) afterExecution.getOutputs().get("values");
        assertThat(outputs.get("state"), is("FAILED"));
    }

    @SuppressWarnings("unchecked")
    public void shouldCallTasksAfterListener(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(3));

        TaskRun taskRun = execution.getTaskRunList().getFirst();
        assertThat(taskRun.getState().getCurrent(), is(SUCCESS));

        TaskRun listenerTaskRun = execution.getTaskRunList().get(1);
        assertThat(taskRun.getState().getCurrent(), is(SUCCESS));
        assertThat(listenerTaskRun.getState().getStartDate(), sameOrAfter(taskRun.getState().getEndDate().orElseThrow()));

        TaskRun afterExecution = execution.getTaskRunList().getLast();
        assertThat(afterExecution.getState().getCurrent(), is(SUCCESS));
        assertThat(afterExecution.getState().getStartDate(), sameOrAfter(listenerTaskRun.getState().getEndDate().orElseThrow()));
        assertThat(afterExecution.getState().getStartDate(), sameOrAfter(execution.getState().getEndDate().orElseThrow()));
        Map<String, Object> outputs = (Map<String, Object> ) afterExecution.getOutputs().get("values");
        assertThat(outputs.get("state"), is("SUCCESS"));
    }
}
