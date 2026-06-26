package io.kestra.plugin.core.preview;

import io.kestra.core.preview.FilePreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageFileRendererTest {
    private final ImageFileRenderer renderer = new ImageFileRenderer();

    @Test
    void shouldRenderAsBase64() throws IOException {
        // Given
        byte[] imageBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG header
        InputStream inputStream = new ByteArrayInputStream(imageBytes);

        // When
        FilePreview rendered = renderer.render("png", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.getContent()).isEqualTo(Base64.getEncoder().encodeToString(imageBytes));
    }

    @Test
    void shouldReturnImageTypeAndExtension() throws IOException {
        // Given
        InputStream inputStream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        // When
        FilePreview rendered = renderer.render("png", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.getType()).isEqualTo(FilePreview.Type.IMAGE);
        assertThat(rendered.getExtension()).isEqualTo("png");
    }

    @Test
    void shouldNeverBeTruncated() throws IOException {
        // Given
        InputStream inputStream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        // When
        FilePreview rendered = renderer.render("jpg", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.isTruncated()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "jpeg", "svg", "gif", "bmp", "webp"})
    void shouldSupportImageExtensions(String extension) throws IOException {
        // Given
        InputStream inputStream = new ByteArrayInputStream(new byte[]{1, 2, 3});

        // When
        FilePreview rendered = renderer.render(extension, inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.getType()).isEqualTo(FilePreview.Type.IMAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PNG", "JPG", "JPEG", "SVG", "GIF", "BMP", "WEBP"})
    void shouldSupportUppercaseExtensions(String extension) {
        assertThat(renderer.supports(extension)).isTrue();
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
    void shouldNotSupportNonImageExtensions() {
        assertThat(renderer.supports("pdf")).isFalse();
        assertThat(renderer.supports("txt")).isFalse();
        assertThat(renderer.supports("ion")).isFalse();
    }
}
