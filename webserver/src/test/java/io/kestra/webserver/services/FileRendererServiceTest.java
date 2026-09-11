package io.kestra.webserver.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.plugins.PluginAutoInstallService;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.preview.FilePreview;
import io.kestra.core.preview.FileRenderer;
import io.kestra.plugin.core.preview.TextFileRenderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileRendererServiceTest {

    private PluginRegistry pluginRegistry;
    private PluginAutoInstallService pluginAutoInstallService;
    private FileRendererService service;

    @BeforeEach
    void setUp() {
        pluginRegistry = mock(PluginRegistry.class);
        pluginAutoInstallService = mock(PluginAutoInstallService.class);
        service = new FileRendererService(pluginRegistry, pluginAutoInstallService);
    }

    @Test
    void shouldReturnRegisteredRendererWithoutInstallingAnything() {
        // Given
        when(pluginRegistry.plugins()).thenReturn(List.of(pluginWith(AvroRenderer.class)));

        // When
        FileRenderer renderer = service.resolve("avro");

        // Then
        assertThat(renderer).isInstanceOf(AvroRenderer.class);
        verify(pluginAutoInstallService, never()).installRendererForExtension("avro");
    }

    @Test
    void shouldInstallRendererThenResolveItWhenExtensionIsNotCoveredLocally() {
        // Given — nothing supports avro until the install registers the plugin providing it
        when(pluginRegistry.plugins())
            .thenReturn(List.of())
            .thenReturn(List.of(pluginWith(AvroRenderer.class)));
        when(pluginAutoInstallService.installRendererForExtension("avro")).thenReturn(true);

        // When
        FileRenderer renderer = service.resolve("avro");

        // Then
        assertThat(renderer).isInstanceOf(AvroRenderer.class);
        verify(pluginAutoInstallService).installRendererForExtension("avro");
    }

    @Test
    void shouldFallBackToTextRendererWhenNoPluginProvidesOne() {
        // Given
        when(pluginRegistry.plugins()).thenReturn(List.of());
        when(pluginAutoInstallService.installRendererForExtension("avro")).thenReturn(false);

        // When
        FileRenderer renderer = service.resolve("avro");

        // Then
        assertThat(renderer).isInstanceOf(TextFileRenderer.class);
    }

    private static RegisteredPlugin pluginWith(Class<? extends FileRenderer> renderer) {
        return RegisteredPlugin.builder().fileRenderers(List.of(renderer)).build();
    }

    public static class AvroRenderer implements FileRenderer {
        @Override
        public boolean supports(String extension) {
            return "avro".equalsIgnoreCase(extension);
        }

        @Override
        public Set<String> extensions() {
            return Set.of("avro");
        }

        @Override
        public FilePreview render(String extension, InputStream inputStream, Optional<Charset> charset, int maxRows) throws IOException {
            return FilePreview.builder().extension(extension).type(FilePreview.Type.LIST).content(List.of()).build();
        }
    }
}
