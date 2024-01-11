package io.kestra.core.tasks.storages;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get the size of a file from the internal storage."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "uri: \"kestra://long/url/file.txt\""
            }
        )
    }
)
public class Size extends Task implements RunnableTask<Size.Output> {
    @Schema(
        title = "the file",
        description = "Must be a `kestra://` storage URL"
    )
    @PluginProperty(dynamic = true)
    private String uri;

    @Override
    public Size.Output run(RunContext runContext) throws Exception {
        StorageInterface storageInterface = runContext.getApplicationContext().getBean(StorageInterface.class);
        URI render = URI.create(runContext.render(this.uri));

        Long size = storageInterface.getAttributes(runContext.getTenantId(), render).getSize();

        return Output.builder()
            .size(size)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The size of the file "
        )
        private final Long size;
    }
}
