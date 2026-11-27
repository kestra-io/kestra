package io.kestra.core.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PluginDocGeneratorTest {
    @Test
    void generatesDocsForListedPluginClasses(@TempDir Path tmp) throws Exception {
        // A compiled-classes dir whose ServiceLoader file lists one plugin class (here a core one,
        // which is on the test classpath); the generator keeps only the classes listed there.
        Path classes = tmp.resolve("classes");
        Path serviceFile = classes.resolve("META-INF/services/io.kestra.core.models.Plugin");
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, "io.kestra.plugin.core.log.Log\n");

        Path out = tmp.resolve("out");

        PluginDocGenerator.generate(classes, out);

        Path markdown = out.resolve("io.kestra.plugin.core.log.Log.md");
        Path schema = out.resolve("io.kestra.plugin.core.log.Log.json");
        assertThat(Files.exists(markdown)).isTrue();
        assertThat(Files.exists(schema)).isTrue();
        assertThat(Files.readString(schema)).contains("properties");
        // only the listed class is written, not every plugin on the classpath
        try (var files = Files.list(out)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }
}
