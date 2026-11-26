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

    private static final String FILE_NAME = "plugin-template-test-1.0.0-SNAPSHOT.jar";

    private PluginTemplateJar() {
    }

    /**
     * Copies the fixture JAR into the given directory, which must already exist.
     *
     * @return the path of the copied JAR
     */
    public static Path copyTo(final Path pluginsDirectory) throws IOException {
        try (InputStream jar = PluginTemplateJar.class.getClassLoader().getResourceAsStream("plugins/" + FILE_NAME)) {
            Objects.requireNonNull(jar, "The plugin fixture '%s' is missing from the test classpath.".formatted(FILE_NAME));
            Path target = pluginsDirectory.resolve(FILE_NAME);
            Files.copy(jar, target);
            return target;
        }
    }
}
