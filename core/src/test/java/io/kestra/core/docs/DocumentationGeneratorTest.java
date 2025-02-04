package io.kestra.core.docs;

import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.plugin.core.runner.Process;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.plugin.core.debug.Echo;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Dag;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.state.Set;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class DocumentationGeneratorTest {
    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    DocumentationGenerator documentationGenerator;

    @Test
    void tasks() throws URISyntaxException, IOException {
        Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

        assertThat(scan.size(), is(1));
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan.getFirst(), scan.getFirst().getTasks().getFirst(), Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("ExampleTask"));
        assertThat(render, containsString("description: \"Short description for this task\""));
        assertThat(render, containsString("`VALUE_1`"));
        assertThat(render, containsString("`VALUE_2`"));
        assertThat(render, containsString("This plugin is exclusively available on the Cloud and Enterprise editions of Kestra."));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void dag() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class dag = scan.findClass(Dag.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, dag, Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("Dag"));
        assertThat(render, containsString("**Required:** ✔️"));
        assertThat(render, containsString("`concurrent`"));
        assertThat(render, not(containsString("requires an Enterprise Edition")));

        int propertiesIndex = render.indexOf("Properties");
        int definitionsIndex = render.indexOf("Definitions");

        assertRequiredPropsAreFirst(render.substring(propertiesIndex, definitionsIndex));

        String definitionsDoc = render.substring(definitionsIndex);
        Arrays.stream(definitionsDoc.split("[^#]### "))
            // first is 'Definitions' header
            .skip(1)
                .forEach(DocumentationGeneratorTest::assertRequiredPropsAreFirst);
    }

    private static void assertRequiredPropsAreFirst(String propertiesDoc) {
        int lastRequiredPropIndex = propertiesDoc.lastIndexOf("* **Required:** ✔️");
        int firstOptionalPropIndex = propertiesDoc.indexOf("* **Required:** ❌");
        if (lastRequiredPropIndex != -1 && firstOptionalPropIndex != -1) {
            assertThat(lastRequiredPropIndex, lessThanOrEqualTo(firstOptionalPropIndex));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void returnDoc() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class returnTask = scan.findClass(Return.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, returnTask, Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("Return a value for debugging purposes."));
        assertThat(render, containsString("is intended for troubleshooting"));
        assertThat(render, containsString("## Metrics"));
        assertThat(render, containsString("### `length`\n" + "* **Type:** ==counter== "));
        assertThat(render, containsString("### `duration`\n" + "* **Type:** ==timer== "));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void defaultBool() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class bash = scan.findClass(Subflow.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, bash, Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("* **Default:** `false`"));
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @Test
    void echo() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<Echo> bash = scan.findClass(Echo.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, bash, Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("Echo"));
        assertThat(render, containsString("This feature is deprecated and will be removed in the future"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void state() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<Set> set = scan.findClass(Set.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, set, Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("Set"));
        assertThat(render, containsString("::alert{type=\"warning\"}\n"));
    }

    @Test
    void pluginDoc() throws Exception {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin core = pluginScanner.scan();

        List<Document> docs = documentationGenerator.generate(core);
        Document doc = docs.getFirst();
        assertThat(doc.getIcon(), is(notNullValue()));
        assertThat(doc.getBody(), containsString("## <img width=\"25\" src=\"data:image/svg+xml;base64,"));
    }

    @Test
    void pluginEeDoc() throws Exception {
        Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        List<RegisteredPlugin> list = pluginScanner.scan(plugins);

        List<Document> docs = documentationGenerator.generate(list.stream().filter(r -> r.license() != null).findFirst().orElseThrow());
        Document doc = docs.getFirst();
        assertThat(doc.getBody(), containsString("This plugin is exclusively available on the Cloud and Enterprise editions of Kestra."));
    }

    @SuppressWarnings("unchecked")
    @Test
    void taskRunner() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<Process> processTaskRunner = scan.findClass(Process.class.getName()).orElseThrow();

        ClassPluginDocumentation<Process> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, processTaskRunner, Process.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("title: Process"));
        assertThat(render, containsString("Task runner that executes a task as a subprocess on the Kestra host."));
    }
}
