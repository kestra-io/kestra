package io.kestra.plugin.core.preview;

import io.kestra.core.preview.FilePreview;
import io.kestra.core.preview.FileRenderer;
import io.kestra.core.serializers.FileSerde;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Text file renderer",
    description = "Preview text files inside the Kestra UI, supported extensions: txt, md."
)
public class TextFileRenderer implements FileRenderer {
    private static final int MAX_SIZE_IN_BYTES = 2_097_152; // 2 MB

    @Override
    public boolean supports(String extension) {
        return "txt".equalsIgnoreCase(extension) || ".md".equalsIgnoreCase(extension);
    }

    @Override
    public FilePreview render(String extension, InputStream inputStream, Optional<Charset> charset, int maxRows) throws IOException {
        // NOTE: at runtime, support all extensions as it is used as the default
        boolean truncated = false;
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset.orElse(StandardCharsets.UTF_8)), FileSerde.BUFFER_SIZE)) {
            String line = reader.readLine();
            int lineCount = 0;

            while (line != null && lineCount < maxRows) {
                contentBuilder.append(line);
                lineCount++;
                if ((line = reader.readLine()) != null) {
                    contentBuilder.append("\n");

                    if (lineCount == maxRows) {
                        truncated = true;
                        break;
                    }
                }
            }
        }

        var content = contentBuilder.toString();
        Charset effectiveCharset = charset.orElse(StandardCharsets.UTF_8);
        byte[] inputBytes = content.getBytes(effectiveCharset);
        if (inputBytes.length > MAX_SIZE_IN_BYTES) {
            byte[] truncatedBytes = new byte[MAX_SIZE_IN_BYTES];
            System.arraycopy(inputBytes, 0, truncatedBytes, 0, MAX_SIZE_IN_BYTES);
            content = new String(truncatedBytes, effectiveCharset);
            truncated = true;
        }

        return FilePreview.builder()
            .truncated(truncated)
            .content(content)
            .extension(extension)
            .type(computeType(extension))
            .build();
    }

    private FilePreview.Type computeType(String extension) {
        return switch (extension.toLowerCase()) {
            case "md"-> FilePreview.Type.MARKDOWN;
            default -> FilePreview.Type.TEXT;
        };
    }
}
