package io.kestra.plugin.core.preview;

import io.kestra.core.preview.FilePreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TextFileRendererTest {
    private final TextFileRenderer renderer = new TextFileRenderer();

    @ParameterizedTest
    @CsvSource({"0, false", "100, false", "101, true"})
    void shouldTruncateByLineCount(int lineCount, boolean expectedTruncated) throws IOException {
        // Given
        String content = "line\n".repeat(lineCount);
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // When
        FilePreview rendered = renderer.render("txt", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.isTruncated()).isEqualTo(expectedTruncated);
    }

    @Test
    void shouldTruncateBySize() throws IOException {
        // Given — content larger than 2MB limit
        byte[] largeContent = new byte[2_200_000];
        java.util.Arrays.fill(largeContent, (byte) 'a');
        InputStream inputStream = new ByteArrayInputStream(largeContent);

        // When
        FilePreview rendered = renderer.render("txt", inputStream, Optional.empty(), Integer.MAX_VALUE);

        // Then
        assertThat(rendered.isTruncated()).isTrue();
        assertThat(rendered.getContent().toString().length()).isLessThanOrEqualTo(2_097_152);
    }

    @Test
    void shouldReturnTextTypeAndContent() throws IOException {
        // Given
        String content = "hello world";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // When
        FilePreview rendered = renderer.render("txt", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.getType()).isEqualTo(FilePreview.Type.TEXT);
        assertThat(rendered.getContent()).isEqualTo(content);
        assertThat(rendered.getExtension()).isEqualTo("txt");
        assertThat(rendered.isTruncated()).isFalse();
    }

    @Test
    void shouldRenderUnknownExtensionsAsText() throws IOException {
        // Given — TextFileRenderer is the default fallback and renders any extension as text
        String content = "col1,col2\nval1,val2";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // When
        FilePreview rendered = renderer.render("csv", inputStream, Optional.empty(), 100);

        // Then
        assertThat(rendered.getType()).isEqualTo(FilePreview.Type.TEXT);
        assertThat(rendered.getContent()).isEqualTo(content);
    }

    @Test
    void shouldSupportTxtExtension() {
        assertThat(renderer.supports("txt")).isTrue();
        assertThat(renderer.supports("TXT")).isTrue();
        assertThat(renderer.supports("csv")).isFalse();
        assertThat(renderer.supports("ion")).isFalse();
    }
}
