package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.NoSuchElementException;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete a file from the Kestra's internal storage."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "uri: \"kestra://long/url/file.txt\""
            }
        )
    },
    aliases = "io.kestra.core.tasks.storages.Delete"
)
public class Delete extends Task implements RunnableTask<Delete.Output> {
    @Schema(
        title = "The file to be deleted.",
        description = "Must be a `kestra://` storage URI."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private String uri;

    @Schema(
        title = "Raise an error if the file is not found."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private final Boolean errorOnMissing = false;

    @Override
    public Delete.Output run(RunContext runContext) throws Exception {
        StorageInterface storageInterface = ((DefaultRunContext)runContext).getApplicationContext().getBean(StorageInterface.class);
        URI render = URI.create(runContext.render(this.uri));

        boolean delete = storageInterface.delete(runContext.tenantId(), render);

        if (errorOnMissing && !delete) {
            throw new NoSuchElementException("Unable to find file '" + render + "'");
        }

        return Output.builder()
            .uri(render)
            .deleted(delete)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The deleted file URI."
        )
        private final URI uri;

        @Schema(
            title = "Whether the file was deleted."
        )
        private final Boolean deleted;
    }
}
