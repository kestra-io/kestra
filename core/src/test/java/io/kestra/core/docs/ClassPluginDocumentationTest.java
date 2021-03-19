package io.kestra.core.docs;

import org.junit.jupiter.api.Test;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ClassPluginDocumentationTest {

    @SuppressWarnings("unchecked")
    @Test
    void tasks() throws URISyntaxException {
        Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

        assertThat(scan.size(), is(1));
        assertThat(scan.get(0).getTasks().size(), is(1));

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(scan.get(0), scan.get(0).getTasks().get(0), Task.class);

        assertThat(doc.getDocExamples().size(), is(2));
        assertThat(doc.getIcon(), is(notNullValue()));
        assertThat(doc.getInputs().size(), is(5));

        // simple
        assertThat(((Map<String, String>) doc.getInputs().get("format")).get("type"), is("string"));
        assertThat(((Map<String, String>) doc.getInputs().get("format")).get("default"), is("{}"));
        assertThat(((Map<String, String>) doc.getInputs().get("format")).get("pattern"), is(".*"));
        assertThat(((Map<String, String>) doc.getInputs().get("format")).get("description"), containsString("of this input"));

        // enum
        assertThat(((List<String>) ((Map<String, Object>) doc.getInputs().get("childInput.childEnum")).get("enum")).size(), is(2));
        assertThat(((List<String>) ((Map<String, Object>) doc.getInputs().get("childInput.childEnum")).get("enum")), containsInAnyOrder("VALUE_1", "VALUE_2"));

        // array
        assertThat((String) ((Map<String, Object>) doc.getInputs().get("childInput.list")).get("type"), is("array"));
        assertThat((String) ((Map<String, Object>) doc.getInputs().get("childInput.list")).get("title"), is("List of string"));
        assertThat((Integer) ((Map<String, Object>) doc.getInputs().get("childInput.list")).get("minItems"), is(1));
        assertThat(((Map<String, String>) ((Map<String, Object>) doc.getInputs().get("childInput.list")).get("items")).get("type"), is("string"));

        // map
        assertThat((String) ((Map<String, Object>) doc.getInputs().get("childInput.map")).get("type"), is("object"));
        assertThat((Boolean) ((Map<String, Object>) doc.getInputs().get("childInput.map")).get("$dynamic"), is(true));
        assertThat(((Map<String, String>) ((Map<String, Object>) doc.getInputs().get("childInput.map")).get("additionalProperties")).get("type"), is("number"));

        // map with object
        assertThat(((Map<String, String>) ((Map<String, Object>) doc.getOutputs().get("childInput.outputChildMap")).get("additionalProperties")).get("type"), is("object"));
        assertThat((((Map<String, Map<String, Map<String, String>>>) ((Map<String, Object>) doc.getOutputs().get("childInput.outputChildMap")).get("additionalProperties")).get("properties")).get("code").get("type"), is("integer"));
    }
}
