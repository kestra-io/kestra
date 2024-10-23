package io.kestra.plugin.core.flow;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "A task that waits for a specified duration before proceeding."
)
@Plugin(
    examples = {
        @Example(
            code = """
                id: wait
                type: io.kestra.plugin.core.debug.Wait
                duration: "PT5S"
            """
        )
    }
)
public class Wait extends Task {
    @Schema(
        title = "Duration to wait",
        description = "The time duration in ISO-8601 format (e.g., `PT5S` for 5 seconds)."
    )
    @PluginProperty
    @NotNull
    private Duration duration;

    public Void run(RunContext runContext) throws Exception {
        runContext.logger().info("Waiting for {}", duration);

        // Wait for the specified duration
        TimeUnit.MILLISECONDS.sleep(duration.toMillis());

        return null;
    }
}
