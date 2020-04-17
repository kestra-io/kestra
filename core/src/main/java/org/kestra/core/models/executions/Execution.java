package org.kestra.core.models.executions;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kestra.core.exceptions.InternalException;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContextLogger;
import org.kestra.core.utils.MapUtils;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;

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

    private String parentId;

    public Execution withState(State.Type state) {
        return new Execution(
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            this.taskRunList,
            this.inputs,
            this.state.withState(state),
            this.parentId
        );
    }

    public boolean hasTaskRun(TaskRun taskRun)  {
        if (this.taskRunList == null) {
            return false;
        }

        return this.taskRunList
            .stream()
            .anyMatch(r -> r.getId().equals(taskRun.getId()));
    }

    public Execution withTaskRun(TaskRun taskRun) throws InternalException {
        ArrayList<TaskRun> newTaskRunList = new ArrayList<>(this.taskRunList);

        boolean b = Collections.replaceAll(
            newTaskRunList,
            this.findTaskRunByTaskRunId(taskRun.getId()),
            taskRun
        );

        if (!b) {
            throw new IllegalStateException("Can't replace taskRun '" + taskRun.getId() + "' on execution'" + this.getId() + "'");
        }

        return new Execution(
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            newTaskRunList,
            this.inputs,
            this.state,
            this.parentId
        );
    }

    public Execution childExecution(String childExecutionId, List<TaskRun> taskRunList, State state) {
        return new Execution(
            childExecutionId,
            this.namespace,
            this.flowId,
            this.flowRevision,
            taskRunList,
            this.inputs,
            state,
            this.id
        );
    }

    public List<TaskRun> findTaskRunsByTaskId(String id) {
        if (this.taskRunList == null) {
            return new ArrayList<>();
        }

        return this.taskRunList
            .stream()
            .filter(taskRun -> taskRun.getTaskId().equals(id))
            .collect(Collectors.toList());
    }

    public TaskRun findTaskRunByTaskRunId(String id) throws InternalException {
        Optional<TaskRun> find = this.taskRunList
            .stream()
            .filter(taskRun -> taskRun.getId().equals(id))
            .findFirst();

        if (find.isEmpty()) {
            throw new InternalException("Can't find taskrun with taskrunId '" + id + "' on execution '" + this.id + "' " + this.toString(true));
        }

        return find.get();
    }

    public TaskRun findTaskRunByTaskIdAndValue(String id, List<String> values) throws InternalException {
        Optional<TaskRun> find = this.getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getTaskId().equals(id) && findChildsValues(taskRun, true).equals(values))
            .findFirst();

        if (find.isEmpty()) {
            throw new InternalException("Can't find taskrun with taskrunId '" + id + "' & value '" + values + "' on execution '" + this.id + "' " + this.toString(true));
        }

        return find.get();
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks
     * Used only from the flow
     *
     * @param resolvedTasks  normal tasks
     * @param resolvedErrors errors tasks
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(List<ResolvedTask> resolvedTasks, List<ResolvedTask> resolvedErrors) {
        return this.findTaskDependingFlowState(resolvedTasks, resolvedErrors, null);
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks
     * <p>
     * if the current have errors, return tasks from errors
     * if not, return the normal tasks
     *
     * @param resolvedTasks  normal tasks
     * @param resolvedErrors errors tasks
     * @param parentTaskRun  the parent task
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

    @SuppressWarnings("UnstableApiUsage")
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

    /**
     * Convert an exception on {@link org.kestra.core.runners.AbstractExecutor} and add log to the current
     * {@code RUNNING} taskRun, on the lastAttempts.
     * If no Attempt is found, we create one (must be nominal case).
     * The executor will catch the {@code FAILED} taskRun emitted and will failed the execution.
     * In the worst case, we FAILED the execution (must not exists).
     *
     * @param e the exception throw from {@link org.kestra.core.runners.AbstractExecutor}
     * @return a new execution with taskrun failed if possible or execution failed is other case
     */
    public Execution failedExecutionFromExecutor(Exception e) {
        return this
            .findFirstByState(State.Type.RUNNING)
            .map(taskRun -> {
                TaskRunAttempt lastAttempt = taskRun.lastAttempt();
                if (lastAttempt == null) {
                    return newAttemptsTaskRunForFailedExecution(taskRun, e);
                } else {
                    return lastAttemptsTaskRunForFailedExecution(taskRun, lastAttempt, e);
                }
            })
            .map(t -> {
                try {
                    return this.withTaskRun(t);
                } catch (InternalException ex) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .orElseGet(() -> this.withState(State.Type.FAILED));
    }

    /**
     * Create a new attemps for failed worker execution
     *
     * @param taskRun the task run where we need to add an attempt
     * @param e the exception raise
     * @return new taskRun with added attempt
     */
    private static TaskRun newAttemptsTaskRunForFailedExecution(TaskRun taskRun, Exception e) {
        return taskRun
            .withAttempts(
                Collections.singletonList(TaskRunAttempt.builder()
                    .state(new State())
                    .logs(RunContextLogger.logEntries(loggingEventFromException(e)).collect(Collectors.toList()))
                    .build()
                    .withState(State.Type.FAILED))
            )
            .withState(State.Type.FAILED);
    }

    /**
     * Add exception log to last attempts
     *
     * @param taskRun the task run where we need to add an attempt
     * @param lastAttempt the lastAttempt found to add
     * @param e the exception raise
     * @return new taskRun with updated attempt with logs
     */
    private static TaskRun lastAttemptsTaskRunForFailedExecution(TaskRun taskRun, TaskRunAttempt lastAttempt, Exception e) {
        List<LogEntry> logs = Stream
            .concat(
                lastAttempt.getLogs().stream(),
                RunContextLogger.logEntries(loggingEventFromException(e))
            )
            .collect(Collectors.toList());

        lastAttempt
            .withLogs(logs)
            .withState(State.Type.FAILED);

        return taskRun
            .withAttempts(
                Stream
                    .concat(
                        taskRun.getAttempts().stream().limit(taskRun.getAttempts().size() - 1),
                        Stream.of(lastAttempt)
                    )
                    .collect(Collectors.toList())
            )
            .withState(State.Type.FAILED);
    }

    /**
     * Transform an exception to {@link ILoggingEvent}
     * @param e the current execption
     * @return the {@link ILoggingEvent} waited to generate {@link LogEntry}
     */
    private static ILoggingEvent loggingEventFromException(Exception e) {
        LoggingEvent loggingEvent = new LoggingEvent();
        loggingEvent.setLevel(ch.qos.logback.classic.Level.ERROR);
        loggingEvent.setThrowableProxy(new ThrowableProxy(e));
        loggingEvent.setMessage(e.getMessage());
        loggingEvent.setThreadName(Thread.currentThread().getName());
        loggingEvent.setTimeStamp(Instant.now().toEpochMilli());
        loggingEvent.setLoggerName(Execution.class.getName());

        return loggingEvent;
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

    public String toStringState() {
        return "(" +
            "\n  state=" + this.getState().getCurrent().toString() +
            "\n  taskRunList=" +
            "\n  [" +
            "\n    " +
            (this.getTaskRunList() == null ? "" : this.getTaskRunList()
                .stream()
                .map(TaskRun::toStringState)
                .collect(Collectors.joining(",\n    "))
            ) +
            "\n  ] " +
            "\n)";
    }

    public Long toCrc32State() {
        CRC32 crc32 = new CRC32();
        crc32.update(this.toStringState().getBytes());

        return crc32.getValue();
    }
}
