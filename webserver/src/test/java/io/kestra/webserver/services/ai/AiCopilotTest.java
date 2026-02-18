package io.kestra.webserver.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kestra.core.exceptions.AiException;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.webserver.services.ai.AbstractAiCopilot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiCopilotTest {

    private static class TestAiCopilot extends AbstractAiCopilot {
        public TestAiCopilot(JsonSchemaGenerator jsonSchemaGenerator, PluginRegistry pluginRegistry, String fallbackPluginVersion) {
            super(jsonSchemaGenerator, pluginRegistry, fallbackPluginVersion);
        }

        @Override
        protected String alreadyValidMessage() {
            return "already-valid";
        }

        @Override
        protected String nonRequestMessage() {
            return "non-request";
        }

        @Override
        protected String unableToGenerateMessage() {
            return "unable-to-generate";
        }

        @Override
        protected List<String> possibleErrorMessages() {
            return List.of("ERROR_MESSAGE");
        }

        @Override
        protected List<String> excludedPluginTypes() {
            return List.of();
        }

        public List<String> publicMostRelevantPlugins(PluginFinder pluginFinder, String userPrompt, List<String> excludedPluginTypes) {
            return this.mostRelevantPlugins(pluginFinder, userPrompt, excludedPluginTypes);
        }

        public static void publicMinifySchema(JsonNode node) {
            minifySchema(node);
        }

        public interface PublicYamlBuilderFn {
            String build(String schemaJson, String generationError, String userPrompt);
        }

        public String publicGenerateYaml(
            PublicYamlBuilderFn builderFn,
            Class<?> modelClass,
            List<String> mostRelevantPluginTypes,
            String generationError,
            List<String> possibleErrorMessages,
            String userPrompt,
            String originalYaml,
            String alreadyValidMessage
        ) {
            return this.generateYaml((schemaJson, genErr, userPr) -> builderFn.build(schemaJson, genErr, userPr), modelClass, mostRelevantPluginTypes, generationError, possibleErrorMessages, userPrompt, originalYaml, alreadyValidMessage);
        }
    }

    @Test
    public void mostRelevantPluginsReturnsResult() {
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);
        RegisteredPlugin registeredPlugin = mock(RegisteredPlugin.class);

        when(registeredPlugin.version()).thenReturn("1.2");
        when(registeredPlugin.allClassGrouped()).thenReturn(Map.of("tasks", List.of(String.class)));

        when(pluginRegistry.plugins()).thenReturn(List.of(registeredPlugin));

        PluginFinder pluginFinder = mock(PluginFinder.class);
        when(pluginFinder.findPlugins(anyString(), eq("my prompt")))
            .thenReturn(List.of("java.lang.String"));

        JsonSchemaGenerator jsonSchemaGenerator = mock(JsonSchemaGenerator.class);

        TestAiCopilot copilot = new TestAiCopilot(jsonSchemaGenerator, pluginRegistry, "1.0");

        List<String> result = copilot.publicMostRelevantPlugins(pluginFinder, "my prompt", List.of());

        assertThat(result).containsExactly("java.lang.String");
    }

    @Test
    public void mostRelevantPluginsEmptyThrows() {
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);
        when(pluginRegistry.plugins()).thenReturn(List.of());

        PluginFinder pluginFinder = mock(PluginFinder.class);
        when(pluginFinder.findPlugins(anyString(), eq("prompt")))
            .thenReturn(List.of());

        JsonSchemaGenerator jsonSchemaGenerator = mock(JsonSchemaGenerator.class);

        TestAiCopilot copilot = new TestAiCopilot(jsonSchemaGenerator, pluginRegistry, "1.0");

        assertThatThrownBy(() -> copilot.publicMostRelevantPlugins(pluginFinder, "prompt", List.of()))
            .isInstanceOf(AiException.class)
            .hasMessage(copilot.unableToGenerateMessage());
    }

    @Test
    public void minifySchemaRemovesDynamicsAndFalseDefault() {
        ObjectNode root = JacksonMapper.ofJson().createObjectNode();
        root.put("$dynamic", "toRemove");
        root.put("$group", "toRemove");
        root.put("default", false);

        ObjectNode props = JacksonMapper.ofJson().createObjectNode();
        ObjectNode child = JacksonMapper.ofJson().createObjectNode();
        child.put("default", false);
        props.set("child", child);
        root.set("properties", props);

        TestAiCopilot.publicMinifySchema(root);

        assertThat(root.has("$dynamic")).isFalse();
        assertThat(root.has("$group")).isFalse();
        assertThat(root.has("default")).isFalse();
        JsonNode childNode = root.path("properties").path("child");
        assertThat(childNode.has("default")).isFalse();
    }

    @Test
    public void generateYamlHappyPathStripsFences() {
        JsonSchemaGenerator jsonSchemaGenerator = mock(JsonSchemaGenerator.class);
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);

        when(jsonSchemaGenerator.schemas(String.class, false, List.of("t"), true))
            .thenReturn(Map.of("type", "object", "properties", Map.of("a", Map.of("default", false))));

        TestAiCopilot copilot = new TestAiCopilot(jsonSchemaGenerator, pluginRegistry, "1.0");

        TestAiCopilot.PublicYamlBuilderFn builderFn = (schemaJson, generationError, userPrompt) -> "```yaml\na: 1\n```";

        String yaml = copilot.publicGenerateYaml(
            builderFn,
            String.class,
            List.of("t"),
            "generation-error",
            copilot.possibleErrorMessages(),
            "user prompt",
            null,
            copilot.alreadyValidMessage()
        );

        assertThat(yaml).contains("a: 1");
        assertThat(yaml).doesNotContain("```");
    }

    @Test
    public void generateYamlReturnsPossibleErrorMessageThrows() {
        JsonSchemaGenerator jsonSchemaGenerator = mock(JsonSchemaGenerator.class);
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);

        when(jsonSchemaGenerator.schemas(String.class, false, List.of(), true))
            .thenReturn(Map.of("type", "object"));

        TestAiCopilot copilot = new TestAiCopilot(jsonSchemaGenerator, pluginRegistry, "1.0");

        TestAiCopilot.PublicYamlBuilderFn builderFn = (schemaJson, generationError, userPrompt) -> "ERROR_MESSAGE";

        assertThatThrownBy(() -> copilot.publicGenerateYaml(
            builderFn,
            String.class,
            List.of(),
            "generation-error",
            copilot.possibleErrorMessages(),
            "user prompt",
            null,
            copilot.alreadyValidMessage()
        ))
            .isInstanceOf(AiException.class)
            .hasMessage("ERROR_MESSAGE");
    }

    @Test
    public void generateYamlAlreadyValidThrows() {
        JsonSchemaGenerator jsonSchemaGenerator = mock(JsonSchemaGenerator.class);
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);

        when(jsonSchemaGenerator.schemas(String.class, false, List.of("t"), true))
            .thenReturn(Map.of("type", "object"));

        TestAiCopilot copilot = new TestAiCopilot(jsonSchemaGenerator, pluginRegistry, "1.0");

        TestAiCopilot.PublicYamlBuilderFn builderFn = (schemaJson, generationError, userPrompt) -> "```yaml\nmy: value\n```";

        assertThatThrownBy(() -> copilot.publicGenerateYaml(
            builderFn,
            String.class,
            List.of("t"),
            "generation-error",
            copilot.possibleErrorMessages(),
            "user prompt",
            "my: value",
            copilot.alreadyValidMessage()
        ))
            .isInstanceOf(AiException.class)
            .hasMessage(copilot.alreadyValidMessage());
    }
}
