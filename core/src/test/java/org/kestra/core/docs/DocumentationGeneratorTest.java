package org.kestra.core.docs;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.plugins.PluginScanner;
import org.kestra.core.plugins.RegisteredPlugin;
import org.kestra.core.tasks.scripts.Bash;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class DocumentationGeneratorTest {
    @Test
    void tasks() throws URISyntaxException, IOException {
        Path plugins = Paths.get(Objects.requireNonNull(PluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(PluginDocumentationTest.class.getClassLoader());
        List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

        assertThat(scan.size(), is(1));
        PluginDocumentation<? extends Task> doc = PluginDocumentation.of(scan.get(0), scan.get(0).getTasks().get(0), Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("# ExampleTask"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void bash() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(PluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class bash = scan.findClass(Bash.class.getName()).orElseThrow();

        PluginDocumentation<? extends Task> doc = PluginDocumentation.of(scan, bash,  Task.class);

        String render = DocumentationGenerator.render(doc);

        System.out.println(render);
        assertThat(render, containsString("# Bash"));
    }
}
