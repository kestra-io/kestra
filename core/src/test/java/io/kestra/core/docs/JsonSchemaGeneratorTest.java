package io.kestra.core.docs;

import io.kestra.core.Helpers;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.GraphStyle;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.tasks.logs.LogExporter;
import io.kestra.core.models.tasks.logs.LogRecord;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.kestra.plugin.core.debug.Echo;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Dag;
import io.kestra.plugin.core.log.Log;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.inject.Inject;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class JsonSchemaGeneratorTest {


    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    PluginRegistry pluginRegistry;

    @BeforeAll
    public static void beforeAll() {
        Helpers.loadExternalPluginsFromClasspath();
    }

    @SuppressWarnings("unchecked")
    @Test
    void tasks() {
        List<RegisteredPlugin> scan = pluginRegistry.externalPlugins();
        Class<? extends Task> cls = scan.getFirst().getTasks().getFirst();

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

            var flow = definitions.get(Flow.class.getName());
            assertThat((List<String>) flow.get("required"), not(contains("deleted")));
            assertThat((List<String>) flow.get("required"), hasItems("id", "namespace", "tasks"));

            Map<String, Object> items = map(
                properties(flow)
                    .get("tasks")
                    .get("items")
            );
            assertThat(items.containsKey("anyOf"), is(false));
            assertThat(items.containsKey("oneOf"), is(true));

            var bash = definitions.get(Log.class.getName());
            assertThat((List<String>) bash.get("required"), not(contains("level")));
            assertThat((String) ((Map<String, Map<String, Object>>) bash.get("properties")).get("level").get("markdownDescription"), containsString("Default value is : `INFO`"));
            assertThat(((String) ((Map<String, Map<String, Object>>) bash.get("properties")).get("message").get("markdownDescription")).contains("can be a string"), is(true));
            assertThat(((Map<String, Map<String, Object>>) bash.get("properties")).get("type").containsKey("pattern"), is(false));
            assertThat((String) bash.get("markdownDescription"), containsString("##### Examples"));
            assertThat((String) bash.get("markdownDescription"), containsString("level: DEBUG"));

            var bashType = definitions.get(Log.class.getName());
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
            var task = definitions.get(Task.class.getName());
            Assertions.assertNotNull(task.get("oneOf"));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void taskRunner() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> generate = jsonSchemaGenerator.schemas(TaskRunner.class);

            var definitions = (Map<String, Map<String, Object>>) generate.get("definitions");
            var taskRunner = definitions.get(TaskRunner.class.getName());
            Assertions.assertNotNull(taskRunner.get("$ref"));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void logShipper() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> generate = jsonSchemaGenerator.schemas(LogExporter.class);

            var definitions = (Map<String, Map<String, Object>>) generate.get("definitions");
            var logShipper = definitions.get(LogExporter.class.getName());
            Assertions.assertNotNull(logShipper.get("$ref"));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void trigger() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Map<String, Object> jsonSchema = jsonSchemaGenerator.generate(AbstractTrigger.class, AbstractTrigger.class);
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

            var dag = definitions.get(Dag.class.getName());
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
            var returnTask = definitions.get(Return.class.getName());
            var metrics = (List<Object>) returnTask.get("$metrics");
            assertThat(metrics.size(), is(2));

            var firstMetric = (Map<String, Object>) metrics.getFirst();
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
            var returnTask = definitions.get(Echo.class.getName());
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
    @Test
    void betaTask() {
        Map<String, Object> generate = jsonSchemaGenerator.properties(Task.class, BetaTask.class);
        assertThat(generate, is(not(nullValue())));
        assertThat(generate.get("$beta"), is(true));
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).size(), is(1));
        assertThat(((Map<String, Map<String, Object>>) generate.get("properties")).get("beta").get("$beta"), is(true));
    }

    @SuppressWarnings("unchecked")
    @Test
    void dashboard() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext) -> {
            Map<String, Object> generate = jsonSchemaGenerator.schemas(Dashboard.class);

            var definitions = (Map<String, Map<String, Object>>) generate.get("definitions");

            String executionTimeSeriesColumnDescriptorExecutionFieldsKey = "io.kestra.plugin.core.dashboard.data.Executions_io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesColumnDescriptor_io.kestra.plugin.core.dashboard.data.Executions-Fields__";
            assertThat(
                properties(definitions.get("io.kestra.plugin.core.dashboard.chart.TimeSeries_io.kestra.plugin.core.dashboard.data.Executions-Fields.io.kestra.plugin.core.dashboard.data.Executions_io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesColumnDescriptor_io.kestra.plugin.core.dashboard.data.Executions-Fields___"))
                    .get("data")
                    .get("$ref"),
                Matchers.is("#/definitions/" + executionTimeSeriesColumnDescriptorExecutionFieldsKey)
            );

            String timeseriesColumnDescriptorExecutionFields = "io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesColumnDescriptor_io.kestra.plugin.core.dashboard.data.Executions-Fields_";
            assertThat(
                ((Map<String, String>) properties(definitions.get("io.kestra.plugin.core.dashboard.data.Executions_io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesColumnDescriptor_io.kestra.plugin.core.dashboard.data.Executions-Fields__"))
                    .get("columns")
                    .get("additionalProperties")
                ).get("$ref"),
                Matchers.is("#/definitions/" + timeseriesColumnDescriptorExecutionFields)
            );

            Map<String, Map<String, Object>> executionTimeseriesProps = properties(definitions.get(timeseriesColumnDescriptorExecutionFields));

            // We verify that it holds TimeSeries-specific props
            assertThat(
                ((List<String>) (
                    executionTimeseriesProps.get("graphStyle")
                ).get("enum")).toArray(),
                Matchers.arrayContainingInAnyOrder(Arrays.stream(GraphStyle.values()).map(Object::toString).toArray())
            );

            // We verify that it holds Executions-specific props
            assertThat(
                ((List<String>) executionTimeseriesProps.get("field").get("enum")).toArray(),
                Matchers.arrayContainingInAnyOrder(Arrays.stream(Executions.Fields.values()).map(Object::toString).toArray())
            );
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> properties(Map<String, Object> generate) {
        return (Map<String, Map<String, Object>>) generate.get("properties");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object object) {
        return (Map<String, Object>) object;
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class TaskWithEnum extends ParentClass implements RunnableTask<VoidOutput>  {

        @PluginProperty
        @Schema(title = "Title from the attribute")
        private TestEnum testEnum;

        @PluginProperty
        @Schema(title = "Title from the attribute")
        private TestClass testClass;

        @PluginProperty
        @Schema(
            title = "Title from the attribute",
            oneOf = {String.class, Example[].class, Example.class}
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
        private static class TestClass {
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
        @Builder.Default
        private Property<String> stringWithDefault = Property.of("default");
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin(
        beta = true
    )
    public static class BetaTask extends Task {
        @PluginProperty(beta = true)
        private String beta;
    }

    public static class TestLogExporter extends LogExporter<VoidOutput> {

        @Override
        public VoidOutput sendLogs(RunContext runContext, Flux<LogRecord> logRecord) throws Exception {
            return null;
        }
    }
}
