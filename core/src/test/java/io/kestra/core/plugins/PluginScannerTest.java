package io.kestra.core.plugins;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PluginScannerTest {
    @Test
    void scanPlugins() throws URISyntaxException {
        Path plugins = Path.of(Objects.requireNonNull(PluginScannerTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(PluginScannerTest.class.getClassLoader());
        List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

        assertThat(scan).hasSize(1);
        assertThat(scan.getFirst().getManifest().getMainAttributes().getValue("X-Kestra-Group")).isEqualTo("io.kestra.plugin.templates");
    }

    @Test
    void scanCore() {
        PluginScanner pluginScanner = new PluginScanner(PluginScannerTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        assertThat(scan.getManifest().getMainAttributes().getValue("X-Kestra-Group")).isEqualTo("io.kestra.plugin.core");
    }
}