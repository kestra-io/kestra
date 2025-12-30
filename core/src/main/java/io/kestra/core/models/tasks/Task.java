package io.kestra.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.flow.WorkingDirectory;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.slf4j.event.Level;

import java.time.Duration;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@Plugin
abstract public class Task implements TaskInterface {
    @Size(max = 256, message = "Task id must be at most 256 characters")
    protected String id;

    protected String type;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    protected String version;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private String description;

    @Valid
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    protected AbstractRetry retry;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    protected Property<Duration> timeout;

    @Builder.Default
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    protected Boolean disabled = false;

    @Valid
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private WorkerGroup workerGroup;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private Level logLevel;

    @Builder.Default
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private boolean allowFailure = false;

    @Builder.Default
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private boolean logToFile = false;

    @Builder.Default
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private String runIf = "true";

    @Builder.Default
    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    private boolean allowWarning = false;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    @Valid
    private Cache taskCache;

    @PluginProperty(hidden = true, group = PluginProperty.CORE_GROUP)
    @Valid
    @Nullable
    private Property<AssetsDeclaration> assets;

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
