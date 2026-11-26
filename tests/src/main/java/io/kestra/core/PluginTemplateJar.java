package io.kestra.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Gives tests the external plugin fixture built from {@code core/src/pluginFixture}, to exercise real JAR loading.
 */
public final class PluginTemplateJar {

    private static final String FILE_NAME = "plugin-template-test.jar";

    private PluginTemplateJar() {
    }

    /**
     * Copies the fixture JAR into the given directory, which must already exist.
     */
    public static void copyTo(final Path pluginsDirectory) throws IOException {
        try (InputStream jar = PluginTemplateJar.class.getClassLoader().getResourceAsStream("plugins/" + FILE_NAME)) {
            Objects.requireNonNull(jar, "The plugin fixture '%s' is missing from the test classpath.".formatted(FILE_NAME));
            Files.copy(jar, pluginsDirectory.resolve(FILE_NAME));
        }
    }
}
