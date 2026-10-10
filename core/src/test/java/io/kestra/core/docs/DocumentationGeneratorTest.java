package io.kestra.core.docs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Execution(ExecutionMode.SAME_THREAD)
class DocumentationGeneratorTest {
    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    DocumentationGenerator documentationGenerator;

    @Test
    void shouldRenderCompleteTaskDocumentation() throws IOException {
        String render = DocumentationGenerator.render(documentation(TestTask.class));

        assertThat(render).contains(
            "title: TestTask",
            "description: \"Documentation test task\"",
            "A task fixture containing every feature rendered by the Markdown documentation generator.",
            "This plugin is currently in beta.",
            "## Examples",
            "> Minimal example",
            "id: \"test_task\"",
            "requiredString: value",
            "> Full JSON example",
            "```json",
            "## Properties",
            "### `requiredString`",
            "* **Dynamic:** ✔️",
            "* **Required:** ✔️",
            "* **Validation regExp:** `[a-z]+`",
            "* **Min length:** `2`",
            "* **Max length:** `10`",
            "This property is currently in beta.",
            "### `booleanWithDefault`",
            "* **Default:** `false`",
            "### `inclusiveInteger`",
            "* **Minimum:** `>= 1`",
            "* **Maximum:** `<= 100`",
            "### `exclusiveDecimal`",
            "* **Minimum:** `> 0`",
            "* **Maximum:** `< 1`",
            "### `items`",
            "* **SubType:** ==string==",
            "* **Min items:** `1`",
            "* **Max items:** `3`",
            "### `namedObjects`",
            "* **Type:** ==object==",
            "### `mode`",
            "* **Possible Values:**",
            "  * `FIRST`",
            "  * `SECOND`",
            "### `formattedValue`",
            "* **Format:** `email`",
            "### `deprecatedProperty`",
            "⚠ Deprecated",
            "## Outputs",
            "### `value`",
            "### `count`",
            "## Definitions",
            "### `io.kestra.core.docs.TestTask-NestedObject`",
            "##### `name`",
            "##### `value`",
            "## Metrics",
            "### `records`",
            "* **Type:** ==counter==  (records)",
            "The number of records.",
            "### `duration`",
            "* **Type:** ==timer==",
            "The processing duration."
        );

        int propertiesIndex = render.indexOf("Properties");
        int outputsIndex = render.indexOf("Outputs");
        assertRequiredPropsAreFirst(render.substring(propertiesIndex, outputsIndex));

        int definitionsIndex = render.indexOf("Definitions");
        String definitionsDoc = render.substring(definitionsIndex);
        Arrays.stream(definitionsDoc.split("[^#]### "))
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

    @Test
    @SuppressWarnings("deprecation")
    void shouldRenderDeprecatedTaskDocumentation() throws IOException {
        String render = DocumentationGenerator.render(documentation(DeprecatedTestTask.class));

        assertThat(render).contains("DeprecatedTestTask");
        assertThat(render).contains("::alert{type=\"warning\"}\n");
    }

    @Test
    void pluginDoc() throws Exception {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin core = pluginScanner.scan();

        List<Document> docs = documentationGenerator.generate(core);
        Document doc = docs.stream().filter(candidate -> candidate.getIcon() != null).findFirst().orElseThrow();
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

    private ClassPluginDocumentation<? extends Task> documentation(Class<? extends Task> task) {
        PluginClassAndMetadata<Task> metadata = new PluginClassAndMetadata<>(
            task,
            Task.class,
            null,
            null,
            null,
            null,
            null
        );
        return ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, "test", false);
    }
}
