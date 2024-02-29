package io.kestra.core.tasks.test;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Files;
import java.nio.file.Paths;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Read extends Task implements RunnableTask<Read.Output> {
    @PluginProperty
    @NotNull
    private String path;

    @Override
    public Read.Output run(RunContext runContext) throws Exception {
        return Output.builder()
            .value(Files.readString(Paths.get(runContext.tempDir().toString(), runContext.render(path))))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The file contents"
        )
        private String value;
    }
}
