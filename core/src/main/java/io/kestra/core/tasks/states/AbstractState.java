package io.kestra.core.tasks.states;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor

public abstract class AbstractState extends Task {
    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {};

    @Schema(
        title = "The name of state file"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @Builder.Default
    protected String name = "default";

    protected Map<String, Object> get(RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        InputStream taskStateFile = runContext.getTaskStateFile("tasks-states", runContext.render(this.name));

        return JacksonMapper.ofJson(false).readValue(taskStateFile, TYPE_REFERENCE);
    }

    protected Pair<URI, Map<String, Object>> merge(RunContext runContext, Map<String, Object> map) throws IllegalVariableEvaluationException, IOException {
        Map<String, Object> current;

        try {
            current = this.get(runContext);
        } catch (FileNotFoundException e) {
            current = Map.of();
        }

        Map<String, Object> merge = MapUtils.merge(current, runContext.render(map));

        URI uri = runContext.putTaskStateFile(
            JacksonMapper.ofJson(false).writeValueAsBytes(merge),
            "tasks-states",
            runContext.render(this.name)
        );

        return Pair.of(uri, merge);
    }

    protected boolean delete(RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        return runContext.deleteTaskStateFile("tasks-states", runContext.render(this.name));
    }
}
