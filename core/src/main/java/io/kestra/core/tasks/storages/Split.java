package io.kestra.core.tasks.storages;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Split files from internal storage on multiple files."
)
@Plugin(
    examples = {
        @Example(
            title = "Split file by file size.",
            code = {
                "from: \"kestra://long/url/file1.txt\"",
                "bytes: 10MB"
            }
        ),
        @Example(
            title = "Split file by rows count.",
            code = {
                "from: \"kestra://long/url/file1.txt\"",
                "rows: 1000"
            }
        ),
        @Example(
            title = "Partition a file in a defined number of partitions.",
            code = {
                "from: \"kestra://long/url/file1.txt\"",
                "partitions: 8"
            }
        ),
    }
)
public class Split extends Task implements RunnableTask<Split.Output> {
    @Schema(
        title = "The file to be splitted."
    )
    @PluginProperty(dynamic = true)
    private String from;

    @Schema(
        title = "Split by file size.",
        description = "Can be provided as a string like \"10MB\" or \"200KB\", or the number of bytes. " +
            "Since we divide storage per line, it's not an hard requirements and files can be a larger."
    )
    @PluginProperty(dynamic = true)
    private String bytes;

    @Schema(
        title = "Split by a fixed number of files."
    )
    @PluginProperty(dynamic = true)
    private Integer partitions;

    @Schema(
        title = "Split by file rows count."
    )
    @PluginProperty(dynamic = true)
    private Integer rows;

    @Schema(
        title = "The separator to used between rows"
    )
    @PluginProperty
    @Builder.Default
    private String separator = "\n";

    @Override
    public Split.Output run(RunContext runContext) throws Exception {
        URI from = new URI(runContext.render(this.from));

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runContext.uriToInputStream(from)));

        List<Path> splited;

        if (this.bytes != null) {
            ReadableBytesTypeConverter readableBytesTypeConverter = new ReadableBytesTypeConverter();
            Number convert = readableBytesTypeConverter.convert(this.bytes, Number.class)
                .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + this.bytes + "'"));

            splited = split(runContext, bufferedReader, (bytes, size) -> bytes >= convert.longValue());
        } else if (this.partitions != null) {
            splited = partition(runContext, bufferedReader, this.partitions);
        } else if (this.rows != null) {
            splited = split(runContext, bufferedReader, (bytes, size) -> size >= this.rows);
        } else {
            throw new IllegalArgumentException("Invalid configuration with no size, count, nor rows");
        }

        return Split.Output.builder()
            .uris(splited
                .stream()
                .map(throwFunction(path -> runContext.putTempFile(path.toFile())))
                .collect(Collectors.toList())
            )
            .build();
    }

    public List<Path> split(RunContext runContext, BufferedReader bufferedReader, BiFunction<Integer, Integer, Boolean> predicate) throws IOException {
        List<Path> files = new ArrayList<>();
        RandomAccessFile write = null;
        int totalBytes = 0;
        int totalRows = 0;
        String row;

        while ((row = bufferedReader.readLine()) != null) {
            if (write == null || predicate.apply(totalBytes, totalRows)) {
                if (write != null) {
                    write.close();
                }

                totalBytes = 0;
                totalRows = 0;

                Path path = runContext.tempFile();
                files.add(path);
                write = new RandomAccessFile(path.toFile(), "rw");
            }

            byte[] bytes = (row + this.separator).getBytes(StandardCharsets.UTF_8);

            write.getChannel().write(ByteBuffer.wrap(bytes));

            totalBytes = totalBytes + bytes.length;
            totalRows = totalRows + 1;
        }

        if (write != null) {
            write.close();
        }

        return files;
    }

    public List<Path> partition(RunContext runContext, BufferedReader bufferedReader, int partition) throws IOException {
        List<Path> files = new ArrayList<>();
        List<RandomAccessFile> writers = new ArrayList<>();

        for (int i = 0; i < partition; i++) {
            Path path = runContext.tempFile();
            files.add(path);

            writers.add(new RandomAccessFile(path.toFile(), "rw"));
        }

        String row;
        int index = 0;
        while ((row = bufferedReader.readLine()) != null) {
            writers.get(index).getChannel().write(ByteBuffer.wrap((row + this.separator).getBytes(StandardCharsets.UTF_8)));

            index = index >= writers.size() - 1 ? 0 : index + 1;
        }

        writers.forEach(throwConsumer(RandomAccessFile::close));

        return files;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The splitted file uris "
        )
        private final List<URI> uris;
    }
}
