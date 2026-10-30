package io.kestra.webserver.services;

import java.util.Objects;
import java.util.Optional;

import io.kestra.core.plugins.PluginAutoInstallService;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.preview.FileRenderer;
import io.kestra.plugin.core.preview.TextFileRenderer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the {@link FileRenderer} to preview a file with.
 * <p>
 * Renderers are plugins, so the set available to an instance is whatever is installed. When none
 * supports the extension, the plugin declaring one is fetched on demand — flow-save detection
 * cannot cover this case, since a renderer type never appears in a flow — and resolution is
 * retried. A file with no renderer at all falls back to {@link TextFileRenderer}, as before.
 */
@Singleton
@Slf4j
public class FileRendererService {

    private final PluginRegistry pluginRegistry;
    private final PluginAutoInstallService pluginAutoInstallService;

    @Inject
    public FileRendererService(
        final PluginRegistry pluginRegistry,
        final PluginAutoInstallService pluginAutoInstallService) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry);
        this.pluginAutoInstallService = Objects.requireNonNull(pluginAutoInstallService);
    }

    /**
     * Returns the renderer to use for the given file extension, installing the plugin that
     * declares one when the extension is not covered locally.
     *
     * @param extension the file extension, without a leading dot; may be {@code null} or blank.
     * @return the renderer supporting that extension, or a {@link TextFileRenderer} fallback.
     */
    public FileRenderer resolve(final String extension) {
        return findRegistered(extension)
            .or(
                () -> pluginAutoInstallService.installRendererForExtension(extension)
                    ? findRegistered(extension)
                    : Optional.empty()
            )
            .orElseGet(TextFileRenderer::new);
    }

    private Optional<FileRenderer> findRegistered(final String extension) {
        return pluginRegistry.plugins().stream()
            .flatMap(registeredPlugin -> registeredPlugin.getFileRenderers().stream())
            .map(this::instantiate)
            .flatMap(Optional::stream)
            .filter(fileRenderer -> fileRenderer.supports(extension))
            .findFirst();
    }

    private Optional<FileRenderer> instantiate(final Class<? extends FileRenderer> fileRenderer) {
        try {
            return Optional.of(fileRenderer.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("Could not instantiate the file renderer '{}'.", fileRenderer.getName(), e);
            return Optional.empty();
        }
    }
}
