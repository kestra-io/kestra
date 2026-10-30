package io.kestra.core.plugins;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Resolves the external plugins directory from the {@code KESTRA_PLUGINS_PATH} environment
 * variable, shared by the CLI ({@code AbstractCommand}) and the auto-install migration.
 */
public final class ExternalPluginsPath {

    private ExternalPluginsPath() {
    }

    /**
     * Returns the plugins directory from {@code KESTRA_PLUGINS_PATH}, or empty when unset.
     */
    public static Optional<Path> fromEnvironment() {
        return Optional.ofNullable(System.getenv("KESTRA_PLUGINS_PATH")).map(Paths::get);
    }
}
