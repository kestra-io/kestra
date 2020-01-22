package org.kestra.core.models.flows;

import com.google.common.base.Objects;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.listeners.Listener;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.runners.RunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
@Builder
public class Flow {
    @NotNull
    private String id;

    public String uid() {
        return String.join("_", Arrays.asList(
            this.getNamespace(),
            this.getId(),
            this.getRevision() != null ? String.valueOf(this.getRevision()) : "-1"
        ));
    }

    @NotNull
    private String namespace;

    @With
    private Integer revision;

    @Valid
    private List<Input> inputs;

    private Map<String, Object> variables;

    @Valid
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

    @Valid
    private List<Listener> listeners;

    @Valid
    private List<Trigger> triggers;

    public Logger logger() {
        return LoggerFactory.getLogger("flow." + this.id);
    }

    public ResolvedTask findTaskByTaskRun(TaskRun taskRun, RunContext runContext) {
        return Stream.of(
            this.tasks,
            this.errors,
            this.listenersTasks()
        )
            .flatMap(tasks -> this.findTaskByTaskId(tasks, taskRun.getTaskId(), runContext, taskRun).stream())
            .map(task -> ResolvedTask.builder()
                .task(task)
                .parentId(taskRun.getParentTaskRunId())
                .value(taskRun.getValue())
                .build()
            )
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Can't find task with id '" + id + "' on flow '" + this.id + "'"));
    }

    public List<Task> listenersTasks() {
        return this.getListeners() != null ?
            this.getListeners()
                .stream()
                .flatMap(listener -> listener.getTasks().stream())
                .collect(Collectors.toList()) :
            new ArrayList<>();
    }

    public boolean isListenerTask(String id) {
        return this.listenersTasks()
            .stream()
            .anyMatch(task -> task.getId().equals(id));
    }

    private Optional<Task> findTaskByTaskId(List<Task> tasks, String id, RunContext runContext, TaskRun taskRun) {
        if (tasks == null) {
            return Optional.empty();
        }

        return tasks
            .stream()
            .flatMap(task -> task.findById(id, runContext, taskRun).stream())
            .findFirst();
    }

    public boolean equalsWithoutRevision(Flow o) {
        if (this == o) return true;

        return Objects.equal(getId(), o.getId()) &&
            Objects.equal(getNamespace(), o.getNamespace()) &&
            Objects.equal(getInputs(), o.getInputs()) &&
            Objects.equal(getTasks(), o.getTasks()) &&
            Objects.equal(getErrors(), o.getErrors()) &&
            Objects.equal(getTriggers(), o.getTriggers());
    }
}
