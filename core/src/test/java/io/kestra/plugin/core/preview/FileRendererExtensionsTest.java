package io.kestra.plugin.core.preview;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.preview.FileRenderer;

import static org.assertj.core.api.Assertions.assertThat;

class FileRendererExtensionsTest {

    private static final List<FileRenderer> CORE_RENDERERS = List.of(
        new TextFileRenderer(),
        new IonFileRenderer(),
        new ImageFileRenderer(),
        new PdfFileRenderer()
    );

    @Test
    void shouldDeclareExtensionsForEveryCoreRenderer() {
        // Given / When / Then — the schema bundle advertises renderers through extensions(), so a
        // renderer that declares nothing is invisible to the on-demand preview install
        assertThat(CORE_RENDERERS).allSatisfy(renderer -> assertThat(renderer.extensions()).isNotEmpty());
    }

    @Test
    void shouldSupportEveryDeclaredExtension() {
        // Given / When / Then — extensions() and supports() must not drift apart
        assertThat(CORE_RENDERERS).allSatisfy(
            renderer -> assertThat(renderer.extensions())
                .allSatisfy(extension -> assertThat(renderer.supports(extension)).isTrue())
        );
    }

    @Test
    void shouldSupportMarkdownFilesWithoutALeadingDot() {
        // Given / When / Then — extensions reach the renderer as "md", never ".md"
        assertThat(new TextFileRenderer().supports("md")).isTrue();
    }
}
