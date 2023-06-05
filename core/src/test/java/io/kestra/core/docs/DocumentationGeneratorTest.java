package io.kestra.core.docs;

import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.tasks.debugs.Echo;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.flows.Flow;
import io.kestra.core.tasks.scripts.Bash;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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
    void bash() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class bash = scan.findClass(Bash.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, bash, Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("Bash"));
        assertThat(render, containsString("**Required:** ✔️"));

        assertThat(render, containsString("`exitOnFailed`"));

        int propertiesIndex = render.indexOf("Properties");
        int outputsIndex = render.indexOf("Outputs");
        int definitionsIndex = render.indexOf("Definitions");

        assertRequiredPropsAreFirst(render.substring(propertiesIndex, outputsIndex));
        assertRequiredPropsAreFirst(render.substring(outputsIndex, definitionsIndex));

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
        assertThat(render, containsString("### `length`\n" + "        * **Type:** ==counter== "));
        assertThat(render, containsString("### `duration`\n" + "        * **Type:** ==timer== "));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void defaultBool() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class bash = scan.findClass(Flow.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, bash, Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("* **Default:** `false`"));
    }

    @Test
    void echo() throws IOException {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin scan = pluginScanner.scan();
        Class<Echo> bash = scan.findClass(Echo.class.getName()).orElseThrow();

        ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, scan, bash, Task.class);

        String render = DocumentationGenerator.render(doc);

        assertThat(render, containsString("Echo"));
        assertThat(render, containsString("- \uD83D\uDD12 Deprecated"));
    }

    @Test
    void pluginDoc() throws Exception {
        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        RegisteredPlugin core = pluginScanner.scan();

        List<Document> docs = documentationGenerator.generate(core);
        Document doc = docs.get(0);
        assertThat(doc.getIcon(), is(notNullValue()));
        assertThat(doc.getBody(), containsString("##  <img width=\"25\" src=\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbG5zOnhsaW5rPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIiB3aWR0aD0iMzIiIGhlaWdodD0iMzIiCiAgICAgcHJlc2VydmVBc3BlY3RSYXRpbz0ieE1pZFlNaWQgbWVldCIgdmlld0JveD0iMCAwIDMyIDMyIgogICAgIHN0eWxlPSItbXMtdHJhbnNmb3JtOiByb3RhdGUoMzYwZGVnKTsgLXdlYmtpdC10cmFuc2Zvcm06IHJvdGF0ZSgzNjBkZWcpOyB0cmFuc2Zvcm06IHJvdGF0ZSgzNjBkZWcpOyI+CiAgICA8ZGVmcy8+CiAgICA8cGF0aCBkPSJNMjAgMjRoLTR2Mmg0djNoOHYtOGgtOHptMi0xaDR2NGgtNHoiIGZpbGw9IiMwRDE1MjMiLz4KICAgIDxwYXRoIGQ9Ik00IDIwdjJoNC41ODZMMiAyOC41ODZMMy40MTQgMzBMMTAgMjMuNDE0VjI4aDJ2LThINHoiIGZpbGw9IiMwRDE1MjMiLz4KICAgIDxwYXRoCiAgICAgICAgZD0iTTI0IDVhMy45OTYgMy45OTYgMCAwIDAtMy44NTggM0gxNHYyaDYuMTQyYTMuOTQgMy45NCAwIDAgMCAuNDI1IDEuMDE5TDE0IDE3LjU4NkwxNS40MTQgMTlsNi41NjctNi41NjdBMy45NTIgMy45NTIgMCAwIDAgMjQgMTNhNCA0IDAgMCAwIDAtOHptMCA2YTIgMiAwIDEgMSAyLTJhMi4wMDIgMi4wMDIgMCAwIDEtMiAyeiIKICAgICAgICBmaWxsPSIjMEQxNTIzIi8+CiAgICA8cGF0aCBkPSJNOS42OTMgMTIuNzVhNSA1IDAgMCAxIDAtNy41bDEuMzI0IDEuNWEzIDMgMCAwIDAgMCA0LjUwMXoiIGZpbGw9IiMwRDE1MjMiLz4KICAgIDxwYXRoIGQ9Ik03LjA0NyAxNS43NTFhOSA5IDAgMCAxIDAtMTMuNTAxbDEuMzI0IDEuNWE3IDcgMCAwIDAgMCAxMC41MDF6IiBmaWxsPSIjMEQxNTIzIi8+Cjwvc3ZnPg==\" /> flows"));
    }
}
