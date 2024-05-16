package io.kestra.core.plugins;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PluginScannerTest {
    @Test
    void scanPlugins() throws URISyntaxException {
        Path plugins = Paths.get(Objects.requireNonNull(PluginScannerTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(PluginScannerTest.class.getClassLoader());
        List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

        assertThat(scan.size(), is(1));
        assertThat(scan.get(0).getManifest().getMainAttributes().getValue("X-Kestra-Group"), is("io.kestra.plugin.templates"));
    }

    @Test
    void scanCore() {
        PluginScanner pluginScanner = new PluginScanner(PluginScannerTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        assertThat(scan.getManifest().getMainAttributes().getValue("X-Kestra-Group"), is("io.kestra.plugin.core"));
    }
}