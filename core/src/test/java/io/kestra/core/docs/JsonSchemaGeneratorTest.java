package io.kestra.core.docs;

import io.kestra.core.Helpers;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.debugs.Echo;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.flows.Dag;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class JsonSchemaGeneratorTest {
    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    private List<RegisteredPlugin> scanPlugins() throws URISyntaxException {
        Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

        PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
        return pluginScanner.scan(plugins);
    }

    @SuppressWarnings("unchecked")
    @Test
    void tasks() throws URISyntaxException {
        List<RegisteredPlugin> scan = scanPlugins();
        Class<? extends Task> cls = scan.get(0).getTasks().get(0);

        Map<String, Object> generate = jsonSchemaGenerator.properties(Task.class, cls);
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).size(), is(5));

        Map<String, Object> format = properties(generate).get("format");
        assertThat(format.get("default"), is("{}"));

        generate = jsonSchemaGenerator.outputs(Task.class, cls);
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).size(), is(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void flow() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> generate = jsonSchemaGenerator.schemas(Flow.class);

            var definitions = (Map<String, Map<String, Object>>) generate.get("definitions");

            var flow = definitions.get("io.kestra.core.models.flows.Flow");
            assertThat((List<String>) flow.get("required"), not(contains("deleted")));
            assertThat((List<String>) flow.get("required"), hasItems("id", "namespace", "tasks"));

            Map<String, Object> items = map(
                properties(flow)
                .get("tasks")
                .get("items")
            );
            assertThat(items.containsKey("anyOf"), is(false));
            assertThat(items.containsKey("oneOf"), is(true));

            var bash = definitions.get("io.kestra.core.tasks.log.Log-1");
            assertThat((List<String>) bash.get("required"), not(contains("level")));
            assertThat((String) ((Map<String, Map<String, Object>>) bash.get("properties")).get("level").get("markdownDescription"), containsString("Default value is : `INFO`"));
            assertThat(((String) ((Map<String, Map<String, Object>>) bash.get("properties")).get("message").get("markdownDescription")).contains("can be a string"), is(true));
            assertThat(((Map<String, Map<String, Object>>) bash.get("properties")).get("type").containsKey("pattern"), is(false));
            assertThat((String) bash.get("markdownDescription"), containsString("##### Examples"));
            assertThat((String) bash.get("markdownDescription"), containsString("level: DEBUG"));

            var bashType = definitions.get("io.kestra.core.tasks.log.Log-2");
            assertThat(bashType, is(notNullValue()));

            var properties = (Map<String, Map<String, Object>>) flow.get("properties");
            var listeners = properties.get("listeners");
            assertThat(listeners.get("$deprecated"), is(true));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void task() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> generate = jsonSchemaGenerator.schemas(Task.class);

            var definitions = (Map<String, Map<String, Object>>) generate.get("definitions");
            var task = definitions.get("io.kestra.core.models.tasks.Task-2");
            var allOf = (List<Object>) task.get("allOf");

            assertThat(allOf.size(), is(1));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void trigger() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> generate = jsonSchemaGenerator.schemas(AbstractTrigger.class);

            var definitions = (Map<String, Map<String, Object>>) generate.get("definitions");
            var task = definitions.get("io.kestra.core.models.triggers.AbstractTrigger-2");
            var allOf = (List<Object>) task.get("allOf");

            assertThat(allOf.size(), is(1));

            Map<String, Object> jsonSchema = jsonSchemaGenerator.generate(AbstractTrigger.class, AbstractTrigger.class);

            System.out.println(jsonSchema.get("properties"));
            assertThat((Map<String, Object>) jsonSchema.get("properties"), allOf(
                Matchers.aMapWithSize(2),
                hasKey("conditions"),
                hasKey("stopAfter")
            ));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void dag() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> generate = jsonSchemaGenerator.schemas(Dag.class);

            var definitions = (Map<String, Map<String, Object>>) generate.get("definitions");

            var dag = definitions.get("io.kestra.core.tasks.flows.Dag-1");
            assertThat((List<String>) dag.get("required"), not(contains("errors")));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnTask() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> returnSchema = jsonSchemaGenerator.schemas(Return.class);
            var definitions = (Map<String, Map<String, Object>>) returnSchema.get("definitions");
            var returnTask = definitions.get("io.kestra.core.tasks.debugs.Return-1");
            var metrics = (List<Object>) returnTask.get("$metrics");
            assertThat(metrics.size(), is(2));

            var firstMetric = (Map<String, Object>) metrics.get(0);
            assertThat(firstMetric.get("name"), is("length"));
            assertThat(firstMetric.get("type"), is("counter"));
            var secondMetric = (Map<String, Object>) metrics.get(1);
            assertThat(secondMetric.get("name"), is("duration"));
            assertThat(secondMetric.get("type"), is("timer"));
        });
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @Test
    void echoTask() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> returnSchema = jsonSchemaGenerator.schemas(Echo.class);
            var definitions = (Map<String, Map<String, Object>>) returnSchema.get("definitions");
            var returnTask = definitions.get("io.kestra.core.tasks.debugs.Echo-1");
            var deprecated = (String) returnTask.get("$deprecated");
            assertThat(deprecated, is("true"));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void testEnum() {
        Map<String, Object> generate = jsonSchemaGenerator.properties(Task.class, TaskWithEnum.class);
        assertThat(generate, is(not(nullValue())));
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).size(), is(4));
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).get("stringWithDefault").get("default"), is("default"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> properties(Map<String, Object> generate) {
        return (Map<String, Map<String, Object>>) generate.get("properties");
    }

    private Map<String, Object> map(Object object) {
        return (Map<String, Object>) object;
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    private static class TaskWithEnum extends ParentClass implements RunnableTask<VoidOutput>  {

        @PluginProperty
        @Schema(title = "Title from the attribute")
        private TestEnum testEnum;

        @PluginProperty
        @Schema(title = "Title from the attribute")
        private TestClass testClass;

        @PluginProperty
        @Schema(
            title = "Title from the attribute",
            anyOf = {String.class, Example[].class, Example.class}
        )
        private Object testObject;

        @Override
        public VoidOutput run(RunContext runContext) throws Exception {
            return null;
        }

        @Schema(title = "Title from the enum")
        private enum TestEnum {
            VALUE1, VALUE2, VALUE3
        }

        @Schema(title = "Test class")
        private class TestClass {
            @Schema(title = "Test property")
            public String testProperty;
        }
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    private static abstract class ParentClass extends Task {
        @PluginProperty
        @Builder.Default
        private String stringWithDefault = "default";
    }
}
