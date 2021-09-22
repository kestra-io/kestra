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
        assertThat(doc.getInputs().size(), is(2));

        // simple
        assertThat(((Map<String, String>) doc.getInputs().get("format")).get("type"), is("string"));
        assertThat(((Map<String, String>) doc.getInputs().get("format")).get("default"), is("{}"));
        assertThat(((Map<String, String>) doc.getInputs().get("format")).get("pattern"), is(".*"));
        assertThat(((Map<String, String>) doc.getInputs().get("format")).get("description"), containsString("of this input"));

        // enum
        Map<String, Object> enumProperties = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput")).get("properties")).get("childEnum");
        assertThat(((List<String>) enumProperties.get("enum")).size(), is(2));
        assertThat(((List<String>) enumProperties.get("enum")), containsInAnyOrder("VALUE_1", "VALUE_2"));

        Map<String, Object> childInput = (Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput")).get("properties");

        // array
        Map<String, Object> childInputList = (Map<String, Object>) childInput.get("list");
        assertThat((String) (childInputList).get("type"), is("array"));
        assertThat((String) (childInputList).get("title"), is("List of string"));
        assertThat((Integer) (childInputList).get("minItems"), is(1));
        assertThat(((Map<String, String>) (childInputList).get("items")).get("type"), is("string"));

        // map
        Map<String, Object> childInputMap = (Map<String, Object>) childInput.get("map");
        assertThat((String) (childInputMap).get("type"), is("object"));
        assertThat((Boolean) (childInputMap).get("$dynamic"), is(true));
        assertThat(((Map<String, String>) (childInputMap).get("additionalProperties")).get("type"), is("number"));

        // output
        Map<String, Object> childOutput = (Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.AbstractTask-OutputChild")).get("properties");
        assertThat(((Map<String, String>) childOutput.get("value")).get("type"), is("string"));
        assertThat(((Map<String, Object>) childOutput.get("outputChildMap")).get("type"), is("object"));
        assertThat(((Map<String, String>)((Map<String, Object>) childOutput.get("outputChildMap")).get("additionalProperties")).get("$ref"), containsString("OutputMap"));

        // required
        Map<String, Object> propertiesChild = (Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput");
        assertThat(((List<String>) propertiesChild.get("required")).size(), is(3));

        // output ref
        Map<String, Object> outputMap = ((Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.AbstractTask-OutputMap")).get("properties"));
        assertThat(outputMap.size(), is(2));
        assertThat(((Map<String, Object>) outputMap.get("code")).get("type"), is("integer"));
    }
}
