package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.FileUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReversedLinesFileReader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Reserve a file from the Kestra's internal storage, last line first."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "from: \"kestra://long/url/file1.txt\"",
            }
        ),
    },
    aliases = "io.kestra.core.tasks.storages.Reverse"
)
public class Reverse extends Task implements RunnableTask<Reverse.Output> {
    @Schema(
        title = "The file to be split."
    )
    @NotNull
    private Property<String> from;

    @Schema(
        title = "The separator used to join the file into chunks. By default, it's a newline `\\n` character. If you are on Windows, you might want to use `\\r\\n` instead."
    )
    @Builder.Default
    private Property<String> separator = Property.of("\n");

    @Schema(
        title = "The name of a supported charset"
    )
    @Builder.Default
    private final Property<String> charset = Property.of(StandardCharsets.UTF_8.name());

    @Override
    public Reverse.Output run(RunContext runContext) throws Exception {
        URI from = new URI(runContext.render(this.from).as(String.class).orElseThrow());
        String extension = FileUtils.getExtension(from);
        String separator = runContext.render(this.separator).as(String.class).orElseThrow();
        Charset charset = Charsets.toCharset(runContext.render(this.charset).as(String.class).orElseThrow());

        File tempFile = runContext.workingDir().createTempFile(extension).toFile();

        File originalFile = runContext.workingDir().createTempFile(extension).toFile();
        try (OutputStream outputStream = new FileOutputStream(originalFile)) {
            IOUtils.copyLarge(runContext.storage().getFile(from), outputStream);
        }

        ReversedLinesFileReader reversedLinesFileReader = ReversedLinesFileReader.builder()
            .setPath(originalFile.toPath())
            .setCharset(charset)
            .get();

        try (
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile));
        ) {
            String line;
            while ((line = reversedLinesFileReader.readLine()) != null) {
                output.write((line + separator).getBytes(charset));
            }
        }

        return Reverse.Output.builder()
            .uri(runContext.storage().putFile(tempFile))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URIs of reverse files in the Kestra's internal storage."
        )
        private final URI uri;
    }
}
