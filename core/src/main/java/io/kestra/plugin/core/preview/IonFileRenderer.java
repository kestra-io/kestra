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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "ION file renderer",
    description = "Preview ION files inside the Kestra UI."
)
public class IonFileRenderer implements FileRenderer {
    @Override
    public boolean supports(String extension) {
        return "ion".equalsIgnoreCase(extension);
    }

    @Override
    public FilePreview render(String extension, InputStream inputStream, Optional<Charset> charset, int maxRows) throws IOException {
        if (!supports(extension)) {
            throw new IllegalArgumentException("Unsupported extension: " + extension);
        }

        try (InputStream bis = new BufferedInputStream(inputStream, FileSerde.BUFFER_SIZE)) {
            List<Object> list = new ArrayList<>();
            boolean truncated = FileSerde.read(bis, maxRows, throwConsumer(list::add));

            return FilePreview.builder()
                .content(list)
                .truncated(truncated)
                .extension(extension)
                .type(FilePreview.Type.LIST)
                .build();
        }
    }
}
