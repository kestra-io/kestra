package io.kestra.plugin.core.state;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.utils.MapUtils;
import io.micronaut.core.annotation.Nullable;
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

    @Schema(
        title = "The name of the state file."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @Builder.Default
    protected String name = "default";

    @Schema(
        title = "Share state for the current namespace.",
        description = "By default, the state is isolated by namespace **and** flow, setting to `true` will allow to share the state between the **same** namespace"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private final Boolean namespace = false;

    @Schema(
        title = "Isolate the state with `taskrun.value`.",
        description = "By default, the state will be isolated with `taskrun.value` (during iteration with each). Setting to `false` will allow using the same state for every run of the iteration."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private final Boolean taskrunValue = true;


    protected Map<String, Object> get(RunContext runContext) throws IllegalVariableEvaluationException, IOException, ResourceExpiredException {
        return JacksonMapper.ofJson(false).readValue(runContext.stateStore().getState(
            !this.namespace,
            "tasks-states",
            runContext.render(this.name),
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
            !this.namespace,
            "tasks-states",
            runContext.render(this.name),
            taskRunValue(runContext),
            JacksonMapper.ofJson(false).writeValueAsBytes(merge)
        );

        return Pair.of(key, merge);
    }

    protected boolean delete(RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        return runContext.stateStore().deleteState(
            !this.namespace,
            "tasks-states",
            runContext.render(this.name),
            taskRunValue(runContext)
        );
    }

    private String taskRunValue(RunContext runContext) {
        return this.taskrunValue ? runContext.storage().getTaskStorageContext().map(StorageContext.Task::getTaskRunValue).orElse(null) : null;
    }
}
