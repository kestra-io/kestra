package io.kestra.plugin.core.preview;

import io.kestra.core.preview.FilePreview;
import io.kestra.core.serializers.FileSerde;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IonFileRendererTest {
    @ParameterizedTest
    @CsvSource({ "0, false", "100, false", "101, true" })
    void testTruncatedByLineCount(int lineCount, boolean truncated) throws IOException {
        File tempFile = File.createTempFile("unit", ".ion");

        try (OutputStream output = new FileOutputStream(tempFile);) {
            for (int i = 0; i < lineCount; i++) {
                FileSerde.write(output, Map.of(1, 2));
            }
        }

        final InputStream is = new DataInputStream(new FileInputStream(tempFile));
        IonFileRenderer renderer = new IonFileRenderer();
        FilePreview rendered = renderer.render("ion", is, Optional.empty(), 100);

        assertThat(rendered.isTruncated()).isEqualTo(truncated);
    }
}