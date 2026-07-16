package io.kestra.plugin.core.preview;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.preview.FilePreview;
import io.kestra.core.serializers.FileSerde;

import static org.assertj.core.api.Assertions.assertThat;

class IonFileRendererTest {
    @ParameterizedTest
    @CsvSource({ "0, false", "100, false", "101, true" })
    void testTruncatedByLineCount(int lineCount, boolean truncated) throws IOException {
        File tempFile = File.createTempFile("unit", ".ion");

        try (OutputStream output = new FileOutputStream(tempFile)) {
            for (int i = 0; i < lineCount; i++) {
                FileSerde.write(output, Map.of(1, 2));
            }
        }

        final InputStream is = new DataInputStream(new FileInputStream(tempFile));
        IonFileRenderer renderer = new IonFileRenderer();
        FilePreview rendered = renderer.render("ion", is, Optional.empty(), 100);

        assertThat(rendered.isTruncated()).isEqualTo(truncated);
        assertThat(rendered.getType()).isEqualTo(FilePreview.Type.LIST);
        List<?> content = (List<?>) rendered.getContent();
        assertThat(content).hasSize(Math.min(lineCount, 100));
    }

    @ParameterizedTest
    @CsvSource({ "1, true", "2, false" })
    void shouldPreviewBinaryIonRows(int maxRows, boolean truncated) throws IOException {
        File tempFile = File.createTempFile("unit", ".ion");

        try (OutputStream output = new FileOutputStream(tempFile)) {
            FileSerde.write(output, Map.of("order_id", "1", "customer_name", "Sagar Khandagre"));
            FileSerde.write(output, Map.of("order_id", "2", "customer_name", "Miguel Moore"));
        }

        try (InputStream inputStream = new DataInputStream(new FileInputStream(tempFile))) {
            IonFileRenderer renderer = new IonFileRenderer();

            FilePreview rendered = renderer.render("ion", inputStream, Optional.empty(), maxRows);

            assertThat(rendered.getExtension()).isEqualTo("ion");
            assertThat(rendered.getType()).isEqualTo(FilePreview.Type.LIST);
            assertThat(rendered.isTruncated()).isEqualTo(truncated);
            assertThat(rendered.getContent())
                .isEqualTo(
                    List.of(
                        Map.of("order_id", "1", "customer_name", "Sagar Khandagre"),
                        Map.of("order_id", "2", "customer_name", "Miguel Moore")
                    ).subList(0, maxRows)
                );
        }
    }
}
