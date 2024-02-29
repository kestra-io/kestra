package io.kestra.core.tasks.test;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Debugging task that returns an encrypted value."
)
@Plugin(
    examples = {
        @Example(
            code = "format: \"Hello World\""
        )
    }
)
public class Encrypted extends Task implements RunnableTask<Encrypted.Output> {
    @Schema(
        title = "The templated string to encrypt."
    )
    @PluginProperty(dynamic = true)
    private String format;

    @Override
    public Encrypted.Output run(RunContext runContext) throws Exception {
        return Encrypted.Output.builder()
            .value(EncryptedString.from(format, runContext))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The encrypted string."
        )
        private EncryptedString value;
    }
}