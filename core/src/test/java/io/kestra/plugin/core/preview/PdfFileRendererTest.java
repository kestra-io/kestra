package io.kestra.plugin.core.preview;

import io.kestra.core.preview.FilePreview;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfFileRendererTest {
    private final PdfFileRenderer renderer = new PdfFileRenderer();

    @Test
    void shouldRenderAsBase64() throws IOException {
        // Given
        byte[] pdfBytes = "%PDF-1.4 fake content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(pdfBytes);

        // When
        FilePreview rendered = renderer.render("pdf", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.getContent()).isEqualTo(Base64.getEncoder().encodeToString(pdfBytes));
    }

    @Test
    void shouldReturnPdfTypeAndExtension() throws IOException {
        // Given
        InputStream inputStream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        // When
        FilePreview rendered = renderer.render("pdf", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.getType()).isEqualTo(FilePreview.Type.PDF);
        assertThat(rendered.getExtension()).isEqualTo("pdf");
    }

    @Test
    void shouldNeverBeTruncated() throws IOException {
        // Given
        InputStream inputStream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        // When
        FilePreview rendered = renderer.render("pdf", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.isTruncated()).isFalse();
    }

    @Test
    void shouldThrowForUnsupportedExtension() {
        // Given
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        // When / Then
        assertThatThrownBy(() -> renderer.render("txt", inputStream, Optional.empty(), 100))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSupportPdfExtension() {
        assertThat(renderer.supports("pdf")).isTrue();
        assertThat(renderer.supports("PDF")).isTrue();
        assertThat(renderer.supports("txt")).isFalse();
        assertThat(renderer.supports("png")).isFalse();
    }
}
