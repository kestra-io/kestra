package org.floworc.core.models.executions;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.Task;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Value
@Builder
public class Execution {
    @NotNull
    private String id;

    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    @NotNull
    private Integer flowRevision;

    @With
    @Builder.Default
    private List<TaskRun> taskRunList = new ArrayList<>();

    @With
    private Map<String, Object> inputs;

    @NotNull
    private State state;

    public Execution withState(State.Type state) {
        return new Execution(
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            this.taskRunList,
            this.inputs,
            this.state.withState(state)
        );
    }

    public TaskRun findTaskRunByTaskId(String id) {
        Optional<TaskRun> find = this.taskRunList
            .stream()
            .filter(taskRun -> taskRun.getTaskId().equals(id))
            .findFirst();

        if (find.isEmpty()) {
            throw new IllegalArgumentException("Can't find taskrun with task id '" + id + "' on execution '" + this.id + "'");
        }

        return find.get();
    }

    public TaskRun findTaskRunById(String id) {
        Optional<TaskRun> find = this.taskRunList
            .stream()
            .filter(taskRun -> taskRun.getId().equals(id))
            .findFirst();

        if (find.isEmpty()) {
            throw new IllegalArgumentException("Can't find taskrun with taskrun id '" + id + "' on execution '" + this.id + "'");
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

        boolean b = Collections.replaceAll(
            newTaskRunList,
            this.findTaskRunById(taskRun.getId()),
            taskRun
        );

        if (!b) {
            throw new IllegalStateException("Can't replace taskRun '" +  taskRun.getId() + "' on execution'" +  this.getId() + "'");
        }

        return new Execution(
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            newTaskRunList,
            this.inputs,
            this.state
        );
    }
}
