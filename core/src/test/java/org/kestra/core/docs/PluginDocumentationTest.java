package org.kestra.core.docs;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.plugins.PluginScanner;
import org.kestra.core.plugins.RegisteredPlugin;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PluginDocumentationTest {
    @Test
    void tasks() throws URISyntaxException {
        Path plugins = Paths.get(Objects.requireNonNull(PluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(PluginDocumentationTest.class.getClassLoader());
        List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

        assertThat(scan.size(), is(1));
        assertThat(scan.get(0).getTasks().size(), is(1));

        PluginDocumentation<? extends Task> doc = PluginDocumentation.of(scan.get(0), scan.get(0).getTasks().get(0));


        assertThat(doc.getExamples().size(), is(2));

        // @FIXME: Don't handle List<T> or Map<K, V>
        assertThat(doc.getInputs().size(), is(3));
        assertThat(doc.getInputs().get("formats").getType(), is("List<String>"));
        assertThat(doc.getInputs().get("nesteds").getType(), is("List<InputNested>"));

        assertThat(doc.getOutputs().size(), is(6));
        assertThat(doc.getOutputs(), hasKey("reverse.reverseLength"));
        assertThat(doc.getOutputs().get("reverse.reverseLength").getType(), is("Integer"));
        assertThat(doc.getOutputs().get("reverse.exampleEnum").getType(), is("ExampleEnum"));
        assertThat(doc.getOutputs().get("reverse.exampleEnum").getValues().size(), is(3));
        assertThat(doc.getOutputs().get("reverse.exampleEnum").getValues(), containsInAnyOrder("VALUE_1", "VALUE_2", "VALUE_3"));
    }
}