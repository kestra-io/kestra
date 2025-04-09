package io.kestra.core.plugins;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;


class PluginArtifactTest {

    @Test
    void shouldParseGivenValidFilenameWithoutClassifier(){
        String fileName = "io_kestra_plugin__plugin-serdes__0_20_0.jar";
        PluginArtifact artifact = PluginArtifact.fromFileName(fileName);

        assertEquals("io.kestra.plugin", artifact.groupId());
        assertEquals("plugin-serdes", artifact.artifactId());
        assertEquals("jar", artifact.extension());
        assertNull(artifact.classifier());
        assertEquals("0.20.0", artifact.version());
        assertNull(artifact.uri());
    }

    @Test
    void shouldParseGivenValidFilenameWithClassifier() {
        String fileName = "io_kestra_plugin__plugin-serdes__custom-classifier__0_20_0.jar";
        PluginArtifact artifact = PluginArtifact.fromFileName(fileName);

        assertEquals("io.kestra.plugin", artifact.groupId());
        assertEquals("plugin-serdes", artifact.artifactId());
        assertEquals("jar", artifact.extension());
        assertEquals("custom-classifier", artifact.classifier());
        assertEquals("0.20.0", artifact.version());
        assertNull(artifact.uri());
    }

    @Test
    void shouldParseGivenValidFilenameWithQualifier() {
        String fileName = "io_kestra_plugin__plugin-serdes__custom-classifier__0_20_0-SNAPSHOT.jar";
        PluginArtifact artifact = PluginArtifact.fromFileName(fileName);

        assertEquals("io.kestra.plugin", artifact.groupId());
        assertEquals("plugin-serdes", artifact.artifactId());
        assertEquals("jar", artifact.extension());
        assertEquals("custom-classifier", artifact.classifier());
        assertEquals("0.20.0-SNAPSHOT", artifact.version());
        assertNull(artifact.uri());
    }

    @Test
    void shouldParseGivenValidFilenameWithNonStandardQualifier() {
        String fileName = "io_kestra_plugin__plugin-serdes__custom-classifier__0_20_0-RC1-SNAPSHOT.jar";
        PluginArtifact artifact = PluginArtifact.fromFileName(fileName);

        assertEquals("io.kestra.plugin", artifact.groupId());
        assertEquals("plugin-serdes", artifact.artifactId());
        assertEquals("jar", artifact.extension());
        assertEquals("custom-classifier", artifact.classifier());
        assertEquals("0.20.0-RC1-SNAPSHOT", artifact.version());
        assertNull(artifact.uri());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionGivenInvalidFilenameMissingVersion() {
        String fileName = "io_kestra_plugin__plugin-serdes__custom-classifier.jar";
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PluginArtifact.fromFileName(fileName)
        );

        assertEquals(
            "Invalid artifact filename 'io_kestra_plugin__plugin-serdes__custom-classifier.jar', expected format is <groupId>__<artifactId>[__<classifier>]__<version>.jar",
            exception.getMessage()
        );
    }

    @Test
    void shouldThrowIllegalArgumentExceptionGivenInvalidFilenameWrongFormat() {
        String fileName = "invalid_filename_format.jar";
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PluginArtifact.fromFileName(fileName)
        );

        assertEquals(
            "Invalid artifact filename 'invalid_filename_format.jar', expected format is <groupId>__<artifactId>[__<classifier>]__<version>.jar",
            exception.getMessage()
        );
    }

    @Test
    void shouldParseGivenValidFilenameEdgeCase() {
        String fileName = "group__artifact__0_0_1.jar";
        PluginArtifact artifact = PluginArtifact.fromFileName(fileName);

        assertEquals("group", artifact.groupId());
        assertEquals("artifact", artifact.artifactId());
        assertEquals("jar", artifact.extension());
        assertNull(artifact.classifier());
        assertEquals("0.0.1", artifact.version());
        assertNull(artifact.uri());
    }
}