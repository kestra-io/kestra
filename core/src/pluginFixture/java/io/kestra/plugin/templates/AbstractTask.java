package io.kestra.plugin.templates;

import java.util.Map;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Short description for this abstract task",
    description = "Full description of this abstract task"
)
public abstract class AbstractTask extends Task implements RunnableTask<AbstractTask.Output> {
    @Schema(
        title = "Short description for this input",
        description = "Full description of this input"
    )
    @Pattern(regexp = ".*")
    protected final String format = "{}";

    @Builder
    @Getter
    @Schema(
        title = "Short description for this class Output",
        description = "Full description of this class Output"
    )
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Short description for this childInput",
            description = "Full description of this childInput"
        )
        private final OutputChild childOutput;
    }

    @Builder
    @Getter
    public static class OutputChild {
        @Schema(
            title = "Short description for this value",
            description = "Full description of this value"
        )
        private final String value;

        @Schema(
            title = "Short description for this map",
            description = "Full description of this map"
        )
        @PluginProperty(additionalProperties = OutputMap.class)
        private final Map<String, OutputMap> outputChildMap;
    }

    @Builder
    @Getter
    public static class OutputMap {
        @Schema(title = "Short description for this output map value")
        private final Integer code;

        @Schema(title = "Short description for this output map display")
        private final String display;
    }
}
