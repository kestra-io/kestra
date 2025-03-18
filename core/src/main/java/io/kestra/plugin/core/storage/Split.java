package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.StorageService;
import io.kestra.core.storages.StorageSplitInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Split a file from the Kestra's internal storage into multiple files."
)
@Plugin(
    examples = {
        @Example(
            title = "Split a file by size.",
            code = {
                "from: \"kestra://long/url/file1.txt\"",
                "bytes: 10MB"
            }
        ),
        @Example(
            title = "Split a file by rows count.",
            code = {
                "from: \"kestra://long/url/file1.txt\"",
                "rows: 1000"
            }
        ),
        @Example(
            title = "Split a file in a defined number of partitions.",
            code = {
                "from: \"kestra://long/url/file1.txt\"",
                "partitions: 8"
            }
        ),
    },
    aliases = "io.kestra.core.tasks.storages.Split"
)
public class Split extends Task implements RunnableTask<Split.Output>, StorageSplitInterface {
    @Schema(
        title = "The file to be split."
    )
    @NotNull
    private Property<String> from;

    private Property<String> bytes;

    private Property<Integer> partitions;

    private Property<Integer> rows;

    @Builder.Default
    private Property<String> separator = Property.of("\n");

    @Override
    public Split.Output run(RunContext runContext) throws Exception {
        URI from = new URI(runContext.render(this.from).as(String.class).orElseThrow());

        return Split.Output.builder()
            .uris(StorageService.split(runContext, this, from))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URIs of split files in the Kestra's internal storage."
        )
        private final List<URI> uris;
    }
}
