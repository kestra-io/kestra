package io.kestra.core.docs;

import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class TestTaskDocumentationTest {
    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    DocumentationGenerator documentationGenerator;

    @Test
    void testTaskDocumentation() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(TestTask.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<TestTask> testTaskClass = scan.findClass(TestTask.class.getName())
            .map(cls -> (Class<TestTask>) cls)
            .orElseThrow();

        PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(
            scan,
            testTaskClass,
            Task.class,
            null
        );

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(
            jsonSchemaGenerator,
            metadata,
            false
        );

        String markdown = DocumentationGenerator.render(doc);
        // Verify title and description
        assertThat(markdown).contains("title: TestTask");
        assertThat(markdown).contains("This is a test task that demonstrates all possible documentation features");

        // Verify properties section
        assertThat(markdown).contains("## Properties");
        assertThat(markdown).contains("### `stringProp`");
        assertThat(markdown).contains("### `intProp`");
        assertThat(markdown).contains("### `boolProp`");
        assertThat(markdown).contains("### `durationProp`");
        assertThat(markdown).contains("### `listProp`");
        assertThat(markdown).contains("### `mapProp`");
        assertThat(markdown).contains("### `nestedProp`");

        // Verify dynamic properties
        assertThat(markdown).contains("* **Dynamic:** ✔️").hasSizeGreaterThanOrEqualTo(3); // stringProp, listProp, mapProp

        // Verify default values
        assertThat(markdown).contains("* **Default:** `default`");
        assertThat(markdown).contains("* **Default:** `false`");
        assertThat(markdown).contains("* **Default:** `PT1H`");

        // Verify validation constraints
        assertThat(markdown).contains("* **Minimum:** `>= 0`");
        assertThat(markdown).contains("* **Maximum:** `<= 100`");

        // Verify nested object properties
        assertThat(markdown).contains("### `name`");
        assertThat(markdown).contains("### `value`");

        // Verify outputs section
        assertThat(markdown).contains("## Outputs");
        assertThat(markdown).contains("### `stringOutput`");
        assertThat(markdown).contains("### `intOutput`");
        assertThat(markdown).contains("### `boolOutput`");
        assertThat(markdown).contains("### `durationOutput`");
        assertThat(markdown).contains("### `listOutput`");
        assertThat(markdown).contains("### `mapOutput`");
        assertThat(markdown).contains("### `nestedOutput`");

        // Verify examples section
        assertThat(markdown).contains("## Examples");
        assertThat(markdown).contains("> Basic usage");
        assertThat(markdown).contains("> Advanced usage with dynamic properties");
        assertThat(markdown).contains("stringProp: hello");
        assertThat(markdown).contains("stringProp: {{ inputs.string }}");

        // Verify property types
        assertThat(markdown).contains("* **Type:** ==string==");
        assertThat(markdown).contains("* **Type:** ==integer==");
        assertThat(markdown).contains("* **Type:** ==boolean==");
        assertThat(markdown).contains("* **Type:** ==array==");
        assertThat(markdown).contains("* **Type:** ==object==");
    }
} 