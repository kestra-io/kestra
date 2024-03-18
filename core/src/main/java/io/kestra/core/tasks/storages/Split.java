package io.kestra.core.tasks.storages;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.StorageService;
import io.kestra.core.storages.StorageSplitInterface;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

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
    }
)
public class Split extends Task implements RunnableTask<Split.Output>, StorageSplitInterface {
    @Schema(
        title = "The file to be split."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private String from;

    private String bytes;

    private Integer partitions;

    private Integer rows;

    @Builder.Default
    private String separator = "\n";

    @Override
    public Split.Output run(RunContext runContext) throws Exception {
        URI from = new URI(runContext.render(this.from));

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
