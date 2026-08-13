package io.kestra.core.plugins;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.plugins.serdes.PluginDeserializer;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class AdditionalPluginTest {

    @Inject
    private JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private ObjectMapper micronautMapper;

    @Test
    @ExecuteFlow("flows/valids/additional-plugin.yaml")
    void additionalPlugin(Execution execution) throws Exception {
        assertThat(execution).isNotNull();
        assertThat(execution.getState().isSuccess()).isTrue();
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("output")).isEqualTo("1 -> Hello");
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().get(1)).get("output")).isEqualTo("Hello World!");
    }

    // Only Jackson3PluginModule's assignability match makes this work: see Jackson3PluginDeserializer.
    @Test
    void shouldDeserializeAnAdditionalPluginThroughTheMicronautMapper() {
        String json = """
            {"type":"%s","baseMessage":"Hello"}""".formatted(AdditionalPluginTest1.class.getName());

        BaseAdditionalPluginTest plugin = micronautMapper.readValue(json, BaseAdditionalPluginTest.class);

        assertThat(plugin).isInstanceOf(AdditionalPluginTest1.class);
        assertThat(plugin.sayHello()).isEqualTo("1 -> Hello");
    }

    // Pins the abstract-only gate of that match: claiming concrete types too would make the deserializer
    // re-invoke itself on the type it just resolved, until the stack runs out.
    @Test
    void shouldDeserializeAConcreteAdditionalPluginWithoutRecursing() {
        String json = """
            {"type":"%s","baseMessage":"Hello"}""".formatted(AdditionalPluginTest1.class.getName());

        AdditionalPluginTest1 plugin = micronautMapper.readValue(json, AdditionalPluginTest1.class);

        assertThat(plugin.sayHello()).isEqualTo("1 -> Hello");
    }

    @Test
    void shouldResolveAdditionalPluginSubtypes() {
        Map<String, Object> generate = jsonSchemaGenerator.properties(null, AdditionalPluginTest.AdditionalPluginTestTask.class);
        var definitions = (Map<String, Map<String, Object>>) generate.get("$defs");
        assertThat(definitions).hasSize(10);
        assertThat(definitions).containsKey("io.kestra.core.plugins.AdditionalPluginTest-AdditionalPluginTest1");
        assertThat(definitions).containsKey("io.kestra.core.plugins.AdditionalPluginTest-AdditionalPluginTest2");
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin
    public static class AdditionalPluginTestTask extends Task implements RunnableTask<AdditionalPluginTestTask.Output> {
        @NotNull
        private BaseAdditionalPluginTest additionalPlugin;

        @Override
        public AdditionalPluginTestTask.Output run(RunContext runContext) throws Exception {
            return Output.builder()
                .output(additionalPlugin.sayHello())
                .build();
        }

        @Builder
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            private String output;
        }
    }

    @Plugin
    @SuperBuilder(toBuilder = true)
    @Getter
    @NoArgsConstructor
    // IMPORTANT: The abstract plugin base class must define using the PluginDeserializer,
    // AND concrete subclasses must be annotated by @JsonDeserialize() to avoid StackOverflow.
    @JsonDeserialize(using = PluginDeserializer.class)
    public static abstract class BaseAdditionalPluginTest extends AdditionalPlugin {
        public abstract String sayHello();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Plugin
    @JsonDeserialize
    public static class AdditionalPluginTest1 extends BaseAdditionalPluginTest {
        @NotNull
        private Property<String> baseMessage;

        @Override
        public String sayHello() {
            return "1 -> " + baseMessage;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @Plugin
    @JsonDeserialize
    public static class AdditionalPluginTest2 extends BaseAdditionalPluginTest {
        @Override
        public String sayHello() {
            return "Hello World!";
        }
    }
}