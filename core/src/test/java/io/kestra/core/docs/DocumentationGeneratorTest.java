package io.kestra.core.docs;

import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.tasks.runners.types.ProcessTaskRunner;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.tasks.debugs.Echo;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.flows.Dag;
import io.kestra.core.tasks.flows.Subflow;
import io.kestra.core.tasks.states.Set;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
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

@MicronautTest
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
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan.get(0), scan.get(0).getTasks().get(0), Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("ExampleTask"));
        assertThat(render, containsString("description: \"Short description for this task\""));
        assertThat(render, containsString("`VALUE_1`"));
        assertThat(render, containsString("`VALUE_2`"));
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

        assertThat(render, containsString("Debugging task that return"));
        assertThat(render, containsString("is mostly useful"));
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
        assertThat(render, containsString("Deprecated"));
    }

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
        Document doc = docs.get(0);
        assertThat(doc.getIcon(), is(notNullValue()));
        assertThat(doc.getBody(), containsString("## <img width=\"25\" src=\"data:image/svg+xml;base64,"));
    }

    @Test
    void taskRunner() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<ProcessTaskRunner> processTaskRunner = scan.findClass(ProcessTaskRunner.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends TaskRunner> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, processTaskRunner, TaskRunner.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("title: ProcessTaskRunner"));
        assertThat(render, containsString("Task runner that executes a task as a subprocess on the Kestra host."));
        assertThat(render, containsString("This plugin is currently in beta"));
    }
}
