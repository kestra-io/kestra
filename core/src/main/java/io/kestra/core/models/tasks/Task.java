package io.kestra.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.flows.WorkingDirectory;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import java.time.Duration;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
abstract public class Task implements TaskInterface {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    protected String id;

    @NotNull
    @NotBlank
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    protected String type;

    private String description;

    @Valid
    protected AbstractRetry retry;

    protected Duration timeout;

    @Builder.Default
    protected Boolean disabled = false;

    @Valid
    private WorkerGroup workerGroup;

    private Level logLevel;

    @Builder.Default
    private boolean allowFailure = false;

    public Optional<Task> findById(String id) {
        if (this.getId().equals(id)) {
            return Optional.of(this);
        }

        if (this.isFlowable()) {
            Optional<Task> childs = ((FlowableTask<?>) this).allChildTasks()
                .stream()
                .map(t -> t.findById(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

            if (childs.isPresent()) {
                return childs;
            }
        }

        return Optional.empty();
    }

    public Optional<Task> findById(String id, RunContext runContext, TaskRun taskRun) throws IllegalVariableEvaluationException {
        if (this.getId().equals(id)) {
            return Optional.of(this);
        }

        if (this.isFlowable()) {
            Optional<Task> childs = ((FlowableTask<?>) this).childTasks(runContext, taskRun)
                .stream()
                .map(throwFunction(resolvedTask -> resolvedTask.getTask().findById(id, runContext, taskRun)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

            if (childs.isPresent()) {
                return childs;
            }
        }

        if (this.isFlowable() && ((FlowableTask<?>) this).getErrors() != null) {
            Optional<Task> errorChilds = ((FlowableTask<?>) this).getErrors()
                .stream()
                .map(throwFunction(task -> task.findById(id, runContext, taskRun)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

            if (errorChilds.isPresent()) {
                return errorChilds;
            }
        }

        return Optional.empty();
    }

    @JsonIgnore
    public boolean isFlowable() {
        return this instanceof FlowableTask;
    }

    @JsonIgnore
    public boolean isSendToWorkerTask() {
        return !(this instanceof FlowableTask) || this instanceof WorkingDirectory;
    }

}
