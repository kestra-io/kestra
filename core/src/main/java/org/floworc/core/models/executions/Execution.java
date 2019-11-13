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

    public TaskRun findTaskRunByTaskRunId(String id) {
        Optional<TaskRun> find = this.taskRunList
            .stream()
            .filter(taskRun -> taskRun.getId().equals(id))
            .findFirst();

        if (find.isEmpty()) {
            throw new IllegalArgumentException("Can't find taskrun with taskrun id '" + id + "' on execution '" + this.id + "'");
        }

        return find.get();
    }

    /**
     * Determine if the current execution is on error & normal tasks
     *
     * if the current have errors, return tasks from errors
     * if not, return the normal tasks
     *
     * @param tasks normal tasks
     * @param errors errors tasks
     * @return the flow we need to follow
     */
    public List<Task> findTaskDependingFlowState(List<Task> tasks, List<Task> errors) {
        List<TaskRun> errorsFlow = this.findTaskRunByTasks(errors);

        if (errorsFlow.size() > 0 || this.hasFailed(tasks)) {
            return errors == null ? new ArrayList<>() : errors;
        }

        return tasks;
    }

    public Task findTaskByTaskRun(List<Task> tasks, TaskRun taskRun) {
        return tasks
            .stream()
            .filter(task -> task.getId().equals(taskRun.getTaskId()))
            .findFirst()
            .orElseThrow();
    }

    public List<TaskRun> findTaskRunByTasks(List<Task> tasks) {
        if (tasks == null) {
            return new ArrayList<>();
        }

        return this
            .getTaskRunList()
            .stream()
            .filter(taskRun -> tasks
                .stream()
                .anyMatch(task -> task.getId().equals(taskRun.getTaskId()))
            )
            .collect(Collectors.toList());
    }

    public List<TaskRun> findRunning(List<Task> tasks) {
        return this.findTaskRunByTasks(tasks)
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .collect(Collectors.toList());
    }

    public Optional<TaskRun> findFirstRunning(List<Task> tasks) {
        return this.findTaskRunByTasks(tasks)
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .findFirst();
    }

    public Optional<TaskRun> findLastByState(List<Task> tasks, State.Type state) {
        return this.findTaskRunByTasks(tasks)
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent() == state)
            .findFirst();
    }

    public Optional<TaskRun> findLastTerminated(List<Task> tasks) {
        List<TaskRun> taskRuns = this.findTaskRunByTasks(tasks);

        ArrayList<TaskRun> reverse = new ArrayList<>(taskRuns);
        Collections.reverse(reverse);

        return reverse
            .stream()
            .filter(taskRun -> taskRun.getState().isTerninated())
            .findFirst();
    }

    public boolean isTerminated(List<Task> tasks) {
        long terminatedCount = this
            .findTaskRunByTasks(tasks)
            .stream()
            .filter(taskRun -> taskRun.getState().isTerninated())
            .count();

        return terminatedCount == tasks.size();
    }

    public boolean hasFailed() {
        return this.taskRunList
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public boolean hasFailed(List<Task> tasks) {
        return this.findTaskRunByTasks(tasks)
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public Execution withTaskRun(TaskRun taskRun) {
        ArrayList<TaskRun> newTaskRunList = new ArrayList<>(this.taskRunList);

        boolean b = Collections.replaceAll(
            newTaskRunList,
            this.findTaskRunByTaskRunId(taskRun.getId()),
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

    public String toString(boolean pretty) {
        if (!pretty) {
            return super.toString();
        }

        return "Execution(" +
            "\n  id=" + this.getId() +
            "\n  flowId=" + this.getFlowId() +
            "\n  state=" + this.getState().getCurrent().toString() +
            "\n  taskRunList=" +
            "\n  [" +
            "\n    " +
            this.getTaskRunList()
                .stream()
                .map(t -> t.toString(true))
                .collect(Collectors.joining(",\n    ")) +
            "\n  ], " +
            "\n  inputs=" + this.getInputs() +
            "\n)";
    }
}
