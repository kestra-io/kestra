package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.Rethrow.throwSupplier;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Write data to a file in the internal storage.",
    description = "Use the Write task to store outputs as files internally and then reference the stored file for processing further down your flow."
)
@Plugin(
    examples = {
        @Example(
            title = "Write data to a file in the internal storage.",
            full = true,
            code = """
                id: write
                namespace: company.team

                tasks:
                - id: write
                  type: io.kestra.plugin.core.storage.Write
                  content: Hello World
                  extension: .txt"""
        )
    }
)
public class Write extends Task implements RunnableTask<Write.Output> {
    @Schema(title = "The file content.")
    @NotNull
    private Property<String> content;

    @Schema(title = "The file extension.")
    private Property<String> extension;


    @Override
    public Write.Output run(RunContext runContext) throws Exception {
        byte[] bytes = runContext.render(content).as(String.class).map(s -> s.getBytes()).orElseThrow();
        Optional<String> maybeExtension = runContext.render(extension).as(String.class);
        Path path = maybeExtension.map(throwFunction(ext -> runContext.workingDir().createTempFile(bytes, ext)))
            .orElseGet(throwSupplier(() -> runContext.workingDir().createTempFile(bytes)));

        return Write.Output.builder()
            .uri(runContext.storage().putFile(path.toFile()))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The created file URI."
        )
        private final URI uri;
    }
}
