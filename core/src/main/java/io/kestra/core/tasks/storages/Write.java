package io.kestra.core.tasks.storages;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.validation.constraints.NotNull;


@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Write data to a file in the internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a file from some data.",
            code = {
                "content: Hello World"
            }
        )
    }
)
public class Write extends Task implements RunnableTask<Write.Output> {
    @Schema(title = "The file content.")
    @NotNull
    @PluginProperty(dynamic = true)
    private String content;

    @Schema(title = "The file extension.")
    @PluginProperty
    private String suffix;


    @Override
    public Write.Output run(RunContext runContext) throws Exception {
        File tempFile = suffix == null ? runContext.tempFile().toFile() :  runContext.tempFile(suffix).toFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
            IOUtils.write(runContext.render(content), fileOutputStream, StandardCharsets.UTF_8);
        }

        return Write.Output.builder()
            .uri(runContext.putTempFile(tempFile))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The created file uri."
        )
        private final URI uri;
    }
}
