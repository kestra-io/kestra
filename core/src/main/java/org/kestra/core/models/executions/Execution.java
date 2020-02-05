package org.kestra.core.models.executions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.utils.MapUtils;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private List<TaskRun> taskRunList;

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

    public List<TaskRun> findTaskRunsByTaskId(String id) {
        return this.taskRunList
            .stream()
            .filter(taskRun -> taskRun.getTaskId().equals(id))
            .collect(Collectors.toList());
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

    public TaskRun findTaskRunByTaskIdAndValue(String id, List<String> values) {
        Optional<TaskRun> find = this.getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getTaskId().equals(id) && findChildsValues(taskRun, true).equals(values))
            .findFirst();

        if (find.isEmpty()) {
            throw new IllegalArgumentException("Can't find taskrun with taskrun id '" + id + "' with values '" + values + "' on execution '" + this.id + "'");
        }

        return find.get();
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks
     * Used only from the flow
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(List<ResolvedTask> resolvedTasks, List<ResolvedTask> resolvedErrors) {
        return this.findTaskDependingFlowState(resolvedTasks, resolvedErrors, null);
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks
     *
     * if the current have errors, return tasks from errors
     * if not, return the normal tasks
     *
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @param parentTaskRun the parent task
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
        if (resolvedTasks == null || this.getTaskRunList() == null) {
            return new ArrayList<>();
        }

        return this
            .getTaskRunList()
            .stream()
            .filter(t -> resolvedTasks
                .stream()
                .anyMatch(resolvedTask -> FlowableUtils.isTaskRunFor(resolvedTask, t, parentTaskRun))
            )
            .collect(Collectors.toList());
    }

    public Optional<TaskRun> findFirstByState(State.Type state) {
        return this.getTaskRunList()
            .stream()
            .filter(t -> t.getState().getCurrent() == state)
            .findFirst();
    }

    public Optional<TaskRun> findLastByState(List<ResolvedTask> resolvedTasks, State.Type state, TaskRun taskRun) {
        return Streams.findLast(this.findTaskRunByTasks(resolvedTasks, taskRun)
            .stream()
            .filter(t -> t.getState().getCurrent() == state)
        );
    }

    public Optional<TaskRun> findLastTerminated(List<ResolvedTask> resolvedTasks, TaskRun taskRun) {
        List<TaskRun> taskRuns = this.findTaskRunByTasks(resolvedTasks, taskRun);

        ArrayList<TaskRun> reverse = new ArrayList<>(taskRuns);
        Collections.reverse(reverse);

        return Streams.findLast(this.findTaskRunByTasks(resolvedTasks, taskRun)
            .stream()
            .filter(t -> t.getState().isTerninated())
        );
    }

    public boolean isTerminatedWithListeners(Flow flow) {
        if (!this.getState().isTerninated()) {
            return false;
        }

        return this.isTerminated(this.findValidListeners(flow));
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
        return this.taskRunList != null && this.taskRunList
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

    public List<ResolvedTask> findValidListeners(Flow flow) {
        if (flow.getListeners() == null) {
            return new ArrayList<>();
        }

        return flow
            .getListeners()
            .stream()
            .filter(listener -> listener.getConditions() == null || listener.getConditions()
                .stream()
                .allMatch(condition -> condition.test(flow, this))
            )
            .flatMap(listener -> listener.getTasks().stream())
            .map(ResolvedTask::of)
            .collect(Collectors.toList());
    }

    public Map<String, Object> outputs() {
        if (this.getTaskRunList() == null) {
            return ImmutableMap.of();
        }

        Map<String, Object> result = new HashMap<>();

        for (TaskRun current : this.taskRunList) {
            if (current.getOutputs() != null) {
                result = MapUtils.merge(result, outputs(current));
            }
        }

        return result;
    }

    private Map<String, Object> outputs(TaskRun taskRun) {
        List<TaskRun> childs = findChilds(taskRun)
            .stream()
            .filter(r -> r.getValue() != null)
            .collect(Collectors.toList());

        if (childs.size() == 0) {
            return Map.of(taskRun.getTaskId(), taskRun.getOutputs());
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> current = result;

        for (TaskRun t : childs) {
            HashMap<String, Object> item = new HashMap<>();
            current.put(t.getValue(), item);
            current = item;
        }

        current.put(taskRun.getValue(), taskRun.getOutputs());

        return Map.of(taskRun.getTaskId(), result);
    }

    /**
     * Find all childs from this {@link TaskRun}. The list is starting from deeper child and end on closest child, so
     * first element is the task that start first.
     * This method don't return the current tasks
     *
     * @param taskRun current child
     * @return List of parent {@link TaskRun}
     */
    public List<TaskRun> findChilds(TaskRun taskRun) {
        if (taskRun.getParentTaskRunId() == null) {
            return new ArrayList<>();
        }

        ArrayList<TaskRun> result = new ArrayList<>();

        boolean ended = false;

        while (!ended) {
            final TaskRun finalTaskRun = taskRun;
            Optional<TaskRun> find = this.taskRunList
                .stream()
                .filter(t -> t.getId().equals(finalTaskRun.getParentTaskRunId()))
                .findFirst();

            if (find.isPresent()) {
                result.add(find.get());
                taskRun = find.get();
            } else {
                ended = true;
            }
        }

        Collections.reverse(result);

        return result;
    }

    public List<String> findChildsValues(TaskRun taskRun, boolean withCurrent) {
        return (withCurrent ?
            Stream.concat(findChilds(taskRun).stream(), Stream.of(taskRun)) :
            findChilds(taskRun).stream()
        )
            .filter(t -> t.getValue() != null)
            .map(TaskRun::getValue)
            .collect(Collectors.toList());
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
            (this.getTaskRunList() == null ? "" : this.getTaskRunList()
                .stream()
                .map(t -> t.toString(true))
                .collect(Collectors.joining(",\n    "))
            ) +
            "\n  ], " +
            "\n  inputs=" + this.getInputs() +
            "\n)";
    }
}
