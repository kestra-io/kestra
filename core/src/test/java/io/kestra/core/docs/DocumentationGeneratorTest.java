package io.kestra.core.docs;

import org.junit.jupiter.api.Test;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.tasks.scripts.Bash;

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
        Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

        assertThat(scan.size(), is(1));
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(scan.get(0), scan.get(0).getTasks().get(0), Task.class);

        String render = DocumentationGenerator.render(doc);

        System.out.println(render);
        assertThat(render, containsString("ExampleTask"));
        assertThat(render, containsString("`VALUE_1`"));
        assertThat(render, containsString("`VALUE_2`"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void bash() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class bash = scan.findClass(Bash.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(scan, bash,  Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("Bash"));
    }
}
