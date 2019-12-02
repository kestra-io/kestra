package org.floworc.core.models.executions;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.ResolvedTask;

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
     * Used only from the flow
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(List<ResolvedTask> resolvedTasks, List<ResolvedTask> resolvedErrors) {
        return this.findTaskDependingFlowState(resolvedTasks, resolvedErrors, null);
    }

    /**
     * Determine if the current execution is on error & normal tasks
     *
     * if the current have errors, return tasks from errors
     * if not, return the normal tasks
     *
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(List<ResolvedTask> resolvedTasks, List<ResolvedTask> resolvedErrors, TaskRun parentTaskRun) {
        List<TaskRun> errorsFlow = this.findTaskRunByTasks(resolvedErrors, parentTaskRun);

        if (errorsFlow.size() > 0 || this.hasFailed(resolvedTasks)) {
            return resolvedErrors == null ? new ArrayList<>() : resolvedErrors;
        }

        return resolvedTasks;
    }

    public List<TaskRun> findTaskRunByTasks(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        if (resolvedTasks == null) {
            return new ArrayList<>();
        }

        return this
            .getTaskRunList()
            .stream()
            .filter(t -> resolvedTasks
                .stream()
                .anyMatch(resolvedTask -> resolvedTask.getTask().getId().equals(t.getTaskId()) &&
                    (
                        parentTaskRun == null || parentTaskRun.getId().equals(t.getParentTaskRunId())
                    )
                )
            )
            .collect(Collectors.toList());
    }

    public Optional<TaskRun> findLastByState(List<ResolvedTask> resolvedTasks, State.Type state, TaskRun taskRun) {
        return this.findTaskRunByTasks(resolvedTasks, taskRun)
            .stream()
            .filter(t -> t.getState().getCurrent() == state)
            .findFirst();
    }

    public Optional<TaskRun> findLastTerminated(List<ResolvedTask> resolvedTasks, TaskRun taskRun) {
        List<TaskRun> taskRuns = this.findTaskRunByTasks(resolvedTasks, taskRun);

        ArrayList<TaskRun> reverse = new ArrayList<>(taskRuns);
        Collections.reverse(reverse);

        return reverse
            .stream()
            .filter(t -> t.getState().isTerninated())
            .findFirst();
    }

    public boolean isTerminated(List<ResolvedTask> resolvedTasks) {
        return this.isTerminated(resolvedTasks, null);
    }

    public boolean isTerminated(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        long terminatedCount = this
            .findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .filter(taskRun -> taskRun.getState().isTerninated())
            .count();

        return terminatedCount == resolvedTasks.size();
    }

    public boolean hasFailed() {
        return this.taskRunList
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public boolean hasFailed(List<ResolvedTask> resolvedTasks) {
        return this.hasFailed(resolvedTasks, null);
    }

    public boolean hasFailed(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.getState().isFailed());
    }

    public Map<String, Object> outputs() {
        return this
            .getTaskRunList()
            .stream()
            .filter(current -> current.getOutputs() != null)
            .map(current -> new AbstractMap.SimpleEntry<>(
                String.join("", Arrays.asList(
                    current.getTaskId(),
                    (current.getParentTaskRunId() != null ? "[" + current.getId() + "]" : "")
                )),
                current.getOutputs())
            )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
