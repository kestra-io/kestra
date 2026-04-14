package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.ListUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
abstract class AbstractBranch<T extends Output> extends Task implements FlowableTask<T> {
    @Valid
    protected List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Valid
    @PluginProperty
    @NotEmpty(message = "The 'tasks' property cannot be empty")
    protected List<Task> tasks;

    @Override
    public List<Task> allChildTasks() {
        return ListUtils.concat(tasks, errors, _finally);
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.getTasks(), parentTaskRun);
    }
}
