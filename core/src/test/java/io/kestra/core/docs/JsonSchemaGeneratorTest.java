package io.kestra.core.docs;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.tasks.scripts.Bash;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class JsonSchemaGeneratorTest {
    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    private List<RegisteredPlugin> scanPlugins() throws URISyntaxException {
        Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        return pluginScanner.scan(plugins);
    }

    @SuppressWarnings("unchecked")
    @Test
    void tasks() throws URISyntaxException {
        List<RegisteredPlugin> scan = scanPlugins();
        Class<? extends Task> cls = scan.get(0).getTasks().get(0);

        Map<String, Object> generate = jsonSchemaGenerator.properties(Task.class, cls);
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).size(), is(2));

        Map<String, Object> format = properties(generate).get("format");
        assertThat(format.get("default"), is("{}"));

        generate = jsonSchemaGenerator.outputs(Task.class, cls);
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).size(), is(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void bash() {
        Map<String, Object> generate = jsonSchemaGenerator.properties(Task.class, Bash.class);

        assertThat(generate, is(not(nullValue())));
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).size(), is(13));

        generate = jsonSchemaGenerator.outputs(Task.class, Bash.class);

        assertThat(generate, is(not(nullValue())));
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).size(), is(6));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> properties(Map<String, Object> generate) {
        return (Map<String, Map<String, Object>>) generate.get("properties");
    }
}
