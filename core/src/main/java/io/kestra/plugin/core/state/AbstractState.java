package io.kestra.plugin.core.state;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.tuple.Pair;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor

public abstract class AbstractState extends Task {
    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {};
    public static final String TASKS_STATES = "tasks-states";

    @Schema(
        title = "The name of the state file."
    )
    @NotNull
    @Builder.Default
    protected Property<String> name = Property.of("default");

    @Schema(
        title = "Share state for the current namespace.",
        description = "By default, the state is isolated by namespace **and** flow, setting to `true` will allow to share the state between the **same** namespace"
    )
    @Builder.Default
    private final Property<Boolean> namespace = Property.of(false);

    @Schema(
        title = "Isolate the state with `taskrun.value`.",
        description = "By default, the state will be isolated with `taskrun.value` (during iteration with each). Setting to `false` will allow using the same state for every run of the iteration."
    )
    @Builder.Default
    private final Property<Boolean> taskrunValue = Property.of(true);


    protected Map<String, Object> get(RunContext runContext) throws IllegalVariableEvaluationException, IOException, ResourceExpiredException {
        return JacksonMapper.ofJson(false).readValue(runContext.stateStore().getState(
            !runContext.render(this.namespace).as(Boolean.class).orElseThrow(),
            TASKS_STATES,
            runContext.render(this.name).as(String.class).orElse(null),
            taskRunValue(runContext)
        ), TYPE_REFERENCE);
    }

    protected Pair<String, Map<String, Object>> merge(RunContext runContext, Map<String, Object> map) throws IllegalVariableEvaluationException, IOException, ResourceExpiredException {
        Map<String, Object> current;

        try {
            current = this.get(runContext);
        } catch (FileNotFoundException e) {
            current = Map.of();
        }

        Map<String, Object> merge = MapUtils.merge(current, runContext.render(map));

        String key = runContext.stateStore().putState(
            !runContext.render(this.namespace).as(Boolean.class).orElseThrow(),
            TASKS_STATES,
            runContext.render(this.name).as(String.class).orElse(null),
            taskRunValue(runContext),
            JacksonMapper.ofJson(false).writeValueAsBytes(merge)
        );

        return Pair.of(key, merge);
    }

    protected boolean delete(RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        return runContext.stateStore().deleteState(
            !runContext.render(this.namespace).as(Boolean.class).orElseThrow(),
            TASKS_STATES,
            runContext.render(this.name).as(String.class).orElse(null),
            taskRunValue(runContext)
        );
    }

    private String taskRunValue(RunContext runContext) throws IllegalVariableEvaluationException {
        return Boolean.TRUE.equals(runContext.render(this.taskrunValue).as(Boolean.class).orElseThrow()) ?
            runContext.storage().getTaskStorageContext().map(StorageContext.Task::getTaskRunValue).orElse(null) : null;
    }
}
