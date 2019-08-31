package org.floworc.core.executions;

import lombok.*;
import lombok.experimental.Wither;
import org.floworc.core.flows.State;
import org.floworc.core.tasks.Task;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Value
@Builder
public class Execution {
    @NotNull
    private String id;

    @NotNull
    private String flowId;

    @Wither
    private List<TaskRun> taskRunList;

    @Wither
    private Context context;

    @NotNull
    private State state;

    public Execution withState(State.Type state) {
        return new Execution(
            this.id,
            this.flowId,
            this.taskRunList,
            this.context,
            this.state.withState(state)
        );
    }

    public TaskRun findTaskRunById(String id) {
        Optional<TaskRun> find = this.taskRunList
            .stream()
            .filter(taskRun -> taskRun.getId().equals(id))
            .findFirst();

        if (find.isEmpty()) {
            throw new IllegalArgumentException("Can't find taskrun with id '" + id + "' on execution '" + this.id + "'");
        }

        return find.get();
    }

    public List<TaskRun> findTaskRunByTask(List<Task> tasks) {
        List<String> taskIds = tasks
            .stream()
            .map(Task::getId)
            .collect(Collectors.toList());

        return this.taskRunList
            .stream()
            .filter(taskRun -> taskIds.contains(taskRun.getTaskId()))
            .collect(Collectors.toList());
    }

    public boolean hasFailed() {
        return this.taskRunList
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public boolean hasFailed(List<Task> tasks) {
        return this.findTaskRunByTask(tasks)
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public Execution withTaskRun(TaskRun taskRun) {
        ArrayList<TaskRun> newTaskRunList = new ArrayList<>(this.taskRunList);

        //noinspection ResultOfMethodCallIgnored
        Collections.replaceAll(
            newTaskRunList,
            this.findTaskRunById(taskRun.getId()),
            taskRun
        );

        return new Execution(
            this.id,
            this.flowId,
            newTaskRunList,
            this.context,
            this.state
        );
    }
}
