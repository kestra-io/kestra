package io.kestra.core.docs;

import io.kestra.core.Helpers;
import io.kestra.core.models.property.DynamicPropertyExampleTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.core.trigger.Schedule;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.assertj.core.api.Assertions.assertThat;

class ClassPluginDocumentationTest {
    @SuppressWarnings("unchecked")
    @Test
    void tasks() throws URISyntaxException {
        Helpers.runApplicationContext(throwConsumer((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

            PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
            List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

            assertThat(scan.size()).isEqualTo(1);
            assertThat(scan.getFirst().getTasks().size()).isEqualTo(1);

            PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(scan.getFirst(), scan.getFirst().getTasks().getFirst(), Task.class, null);
            ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.getFirst().version(), false);

            assertThat(doc.getDocExamples().size()).isEqualTo(2);
            assertThat(doc.getIcon()).isNotNull();
            assertThat(doc.getInputs().size()).isEqualTo(5);
            assertThat(doc.getDocLicense()).isEqualTo("EE");

            // simple
            assertThat(((Map<String, String>) doc.getInputs().get("format")).get("type")).isEqualTo("string");
            assertThat(((Map<String, String>) doc.getInputs().get("format")).get("default")).isEqualTo("{}");
            assertThat(((Map<String, String>) doc.getInputs().get("format")).get("pattern")).isEqualTo(".*");
            assertThat(((Map<String, String>) doc.getInputs().get("format")).get("description")).contains("of this input");

            // definitions
            assertThat(doc.getDefs().size()).isEqualTo(5);

            // enum
            Map<String, Object> enumProperties = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput")).get("properties")).get("childEnum");
            assertThat(((List<String>) enumProperties.get("enum")).size()).isEqualTo(2);
            assertThat(((List<String>) enumProperties.get("enum"))).containsExactlyInAnyOrder("VALUE_1", "VALUE_2");

            Map<String, Object> childInput = (Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput")).get("properties");

            // array
            Map<String, Object> childInputList = (Map<String, Object>) childInput.get("list");
            assertThat((String) (childInputList).get("type")).isEqualTo("array");
            assertThat((String) (childInputList).get("title")).isEqualTo("List of string");
            assertThat((Integer) (childInputList).get("minItems")).isEqualTo(1);
            assertThat(((Map<String, String>) (childInputList).get("items")).get("type")).isEqualTo("string");

            // map
            Map<String, Object> childInputMap = (Map<String, Object>) childInput.get("map");
            assertThat((String) (childInputMap).get("type")).isEqualTo("object");
            assertThat((Boolean) (childInputMap).get("$dynamic")).isTrue();
            assertThat(((Map<String, String>) (childInputMap).get("additionalProperties")).get("type")).isEqualTo("number");

            // output
            Map<String, Object> childOutput = (Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.AbstractTask-OutputChild")).get("properties");
            assertThat(((Map<String, String>) childOutput.get("value")).get("type")).isEqualTo("string");
            assertThat(((Map<String, Object>) childOutput.get("outputChildMap")).get("type")).isEqualTo("object");
            assertThat(((Map<String, String>) ((Map<String, Object>) childOutput.get("outputChildMap")).get("additionalProperties")).get("$ref")).contains("OutputMap");

            // required
            Map<String, Object> propertiesChild = (Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput");
            assertThat(((List<String>) propertiesChild.get("required")).size()).isEqualTo(3);

            // output ref
            Map<String, Object> outputMap = ((Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.AbstractTask-OutputMap")).get("properties"));
            assertThat(outputMap.size()).isEqualTo(2);
            assertThat(((Map<String, Object>) outputMap.get("code")).get("type")).isEqualTo("integer");
        }));
    }

    @SuppressWarnings("unchecked")
    @Test
    void trigger() throws URISyntaxException {
        Helpers.runApplicationContext(throwConsumer((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
            RegisteredPlugin scan = pluginScanner.scan();

            PluginClassAndMetadata<AbstractTrigger> metadata = PluginClassAndMetadata.create(scan, Schedule.class, AbstractTrigger.class, null);
            ClassPluginDocumentation<? extends AbstractTrigger> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), true);

            assertThat(doc.getDefs()).hasSize(23);
            assertThat(doc.getDocLicense()).isNull();

            assertThat(((Map<String, Object>) doc.getDefs().get("io.kestra.core.models.tasks.WorkerGroup")).get("type")).isEqualTo("object");
            assertThat(((Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.core.models.tasks.WorkerGroup")).get("properties")).size()).isEqualTo(2);
        }));
    }

    @Test
    void taskRunner() throws URISyntaxException {
        Helpers.runApplicationContext(throwConsumer((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
            RegisteredPlugin scan = pluginScanner.scan();

            PluginClassAndMetadata<? extends TaskRunner<?>> metadata = PluginClassAndMetadata.create(scan, Process.class, Process.class, null);
            ClassPluginDocumentation<? extends TaskRunner<?>> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), false);

            assertThat(((Map<?, ?>) doc.getPropertiesSchema().get("properties")).get("version")).isNotNull();
            assertThat(doc.getCls()).isEqualTo("io.kestra.plugin.core.runner.Process");
            assertThat(doc.getPropertiesSchema().get("title")).isEqualTo("Task runner that executes a task as a subprocess on the Kestra host.");
            assertThat(doc.getDefs()).isEmpty();
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dynamicProperty() throws URISyntaxException {
        Helpers.runApplicationContext(throwConsumer((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
            RegisteredPlugin scan = pluginScanner.scan();

            PluginClassAndMetadata<DynamicPropertyExampleTask> metadata = PluginClassAndMetadata.create(scan, DynamicPropertyExampleTask.class, DynamicPropertyExampleTask.class, null);
            ClassPluginDocumentation<? extends DynamicPropertyExampleTask> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, scan.version(), true);

            assertThat(doc.getCls()).isEqualTo("io.kestra.core.models.property.DynamicPropertyExampleTask");
            assertThat(doc.getDefs()).hasSize(9);
            Map<String, Object> properties = (Map<String, Object>) doc.getPropertiesSchema().get("properties");
            assertThat(properties).hasSize(22);

            Map<String, Object> number = (Map<String, Object>) properties.get("number");
            assertThat(number.get("anyOf")).isNotNull();
            List<Map<String, Object>> anyOf = (List<Map<String, Object>>) number.get("anyOf");
            assertThat(anyOf).hasSize(2);
            assertThat(anyOf.getFirst().get("type")).isEqualTo("integer");
            assertThat((Boolean) anyOf.getFirst().get("$dynamic")).isTrue();
            assertThat(anyOf.get(1).get("type")).isEqualTo("string");
//            assertThat(anyOf.get(1).get("pattern"), is(".*{{.*}}.*"));

            Map<String, Object> withDefault = (Map<String, Object>) properties.get("withDefault");
            assertThat(withDefault.get("type")).isEqualTo("string");
            assertThat(withDefault.get("default")).isEqualTo("Default Value");
            assertThat((Boolean) withDefault.get("$dynamic")).isTrue();
        }));
    }
}
