package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get the size of a file from Kestra's internal storage."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "uri: \"kestra://long/url/file.txt\""
            }
        )
    },
    aliases = "io.kestra.core.tasks.storages.Size"
)
public class Size extends Task implements RunnableTask<Size.Output> {
    @Schema(
        title = "The file where size needs to be fetched"
    )
    @NotNull
    @PluginProperty(internalStorageURI = true)
    private Property<String> uri;

    @Override
    public Size.Output run(RunContext runContext) throws Exception {
        URI render = URI.create(runContext.render(this.uri).as(String.class).orElseThrow());

        Long size = runContext.storage().getAttributes(render).getSize();

        return Output.builder()
            .size(size)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The size of the file"
        )
        private final Long size;
    }
}
