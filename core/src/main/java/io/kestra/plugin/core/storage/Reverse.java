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

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Reverse a file from the Kestra's internal storage, last line first."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "from: \"kestra://long/url/file1.txt\"",
                "charset: \"UTF-8\"",
                "separator: \"\\n\""
            }
        )
    },
    aliases = "io.kestra.core.storages.Reverse"
)
public class Reverse extends Task implements RunnableTask<Reverse.Output> {
    @Schema(
        title = "The file to be reversed"
    )
    @NotNull
    private Property<String> from;

    @Schema(
        title = "The separator used to join lines. By default, it's a newline `\\n` character. If you are on Windows, you might want to use `\\r\\n` instead."
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

        // Validate charset
        String charsetName = runContext.render(this.charset).as(String.class).orElseThrow();
        if (!Charset.isSupported(charsetName)) {
            throw new IllegalArgumentException("Unsupported charset: " + charsetName);
        }
        Charset charset = Charset.forName(charsetName);

        File tempFile = null;
        File originalFile = null;
        try {
            tempFile = runContext.workingDir().createTempFile(extension).toFile();
            originalFile = runContext.workingDir().createTempFile(extension).toFile();

            // Copy input file
            try (InputStream inputStream = runContext.storage().getFile(from);
                 OutputStream outputStream = new FileOutputStream(originalFile)) {
                if (inputStream.available() == 0) {
                    throw new IllegalArgumentException("Input file is empty");
                }
                IOUtils.copyLarge(inputStream, outputStream);
            }

            // Create reader with specified charset
            try (ReversedLinesFileReader reversedLinesFileReader = ReversedLinesFileReader.builder()
                .setPath(originalFile.toPath())
                .setCharset(charset)
                .get();
                BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {

                String line;
                boolean firstLine = true;
                while ((line = reversedLinesFileReader.readLine()) != null) {
                    if (!firstLine) {
                        output.write(separator.getBytes(charset));
                    }
                    output.write(line.getBytes(charset));
                    firstLine = false;
                }
            }

            return Output.builder()
                .uri(runContext.storage().putFile(tempFile))
                .build();

        } catch (Exception e) {
            // Clean up temporary files in case of error
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            if (originalFile != null && originalFile.exists()) {
                originalFile.delete();
            }
            throw e;
        }
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
