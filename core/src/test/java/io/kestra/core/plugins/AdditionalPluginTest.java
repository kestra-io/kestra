package io.kestra.core.plugins;

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
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class AdditionalPluginTest {

    @Inject
    private JsonSchemaGenerator jsonSchemaGenerator;

    @Test
    @ExecuteFlow("flows/valids/additional-plugin.yaml")
    void additionalPlugin(Execution execution) {
        assertThat(execution).isNotNull();
        assertThat(execution.getState().isSuccess()).isTrue();
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("output")).isEqualTo("1 -> Hello");
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("output")).isEqualTo("Hello World!");
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