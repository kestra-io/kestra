package io.kestra.core.docs;

import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.plugin.core.runner.Process;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.plugin.core.debug.Echo;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Dag;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.state.Set;
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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Execution(ExecutionMode.SAME_THREAD)
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

        assertThat(scan.size()).isEqualTo(1);
        PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(scan.getFirst(), scan.getFirst().getTasks().getFirst(), Task.class, null);
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.getFirst().version(), false);

        String render = DocumentationGenerator.render(doc);

        assertThat(render).contains("ExampleTask");
        assertThat(render).contains("description: \"Short description for this task\"");
        assertThat(render).contains("`VALUE_1`");
        assertThat(render).contains("`VALUE_2`");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void dag() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class dag = scan.findClass(Dag.class.getName()).orElseThrow();

        PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(scan,dag, Task.class, null);
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), false);

        String render = DocumentationGenerator.render(doc);

        assertThat(render).contains("Dag");
        assertThat(render).contains("**Required:** ✔️");
        assertThat(render).contains("`concurrent`");
        assertThat(render).doesNotContain("requires an Enterprise Edition");

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
            assertThat(lastRequiredPropIndex).isLessThanOrEqualTo(firstOptionalPropIndex);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void returnDoc() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class returnTask = scan.findClass(Return.class.getName()).orElseThrow();

        PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(scan, returnTask, Task.class, null);
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), false);

        String render = DocumentationGenerator.render(doc);

        assertThat(render).contains("Return a value for debugging purposes.");
        assertThat(render).contains("This task is mostly useful for troubleshooting.");
        assertThat(render).contains("## Metrics");
        assertThat(render).contains("### `length`\n" + "* **Type:** ==counter== ");
        assertThat(render).contains("### `duration`\n" + "* **Type:** ==timer== ");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void defaultBool() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class bash = scan.findClass(Subflow.class.getName()).orElseThrow();

        PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(scan, bash, Task.class, null);
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), false);

        String render = DocumentationGenerator.render(doc);

        assertThat(render).contains("* **Default:** `false`");
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @Test
    void echo() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<Echo> bash = scan.findClass(Echo.class.getName()).orElseThrow();

        PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(scan, bash, Task.class, null);
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), false);

        String render = DocumentationGenerator.render(doc);

        assertThat(render).contains("Echo");
        assertThat(render).contains("This feature is deprecated and will be removed in the future");
    }

    @SuppressWarnings("unchecked")
    @Test
    void state() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<Set> set = scan.findClass(Set.class.getName()).orElseThrow();

        PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(scan, set, Task.class, null);
        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), false);

        String render = DocumentationGenerator.render(doc);

        assertThat(render).contains("Set");
        assertThat(render).contains("::alert{type=\"warning\"}\n");
    }

    @Test
    void pluginDoc() throws Exception {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin core = pluginScanner.scan();

        List<Document> docs = documentationGenerator.generate(core);
        Document doc = docs.getFirst();
        assertThat(doc.getIcon()).isNotNull();
        assertThat(doc.getBody()).contains("## <img width=\"25\" src=\"data:image/svg+xml;base64,");
    }

    @Test
    void pluginEeDoc() throws Exception {
        Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        List<RegisteredPlugin> list = pluginScanner.scan(plugins);

        List<Document> docs = documentationGenerator.generate(list.stream().filter(r -> r.license() != null).findFirst().orElseThrow());
        Document doc = docs.getFirst();
        assertThat(doc.getBody()).contains("This plugin is exclusively available on the Cloud and Enterprise editions of Kestra.");
    }

    @SuppressWarnings("unchecked")
    @Test
    void taskRunner() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<Process> processTaskRunner = scan.findClass(Process.class.getName()).orElseThrow();

        PluginClassAndMetadata<Process> metadata = PluginClassAndMetadata.create(scan, processTaskRunner, Process.class, null);
        ClassPluginDocumentation<Process> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), false);

        String render = DocumentationGenerator.render(doc);

        assertThat(render).contains("title: Process");
        assertThat(render).contains("Task runner that executes a task as a subprocess on the Kestra host.");
    }
}
