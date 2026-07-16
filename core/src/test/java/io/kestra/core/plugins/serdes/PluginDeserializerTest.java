package io.kestra.core.plugins.serdes;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginRegistry;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PluginDeserializerTest {

    @Mock
    private PluginRegistry registry;

    @Test
    void shouldSucceededDeserializePluginGivenValidType() throws JacksonException {
        // Given
        ObjectMapper om = JsonMapper.builder()
            .addModule(new SimpleModule().addDeserializer(Plugin.class, new PluginDeserializer<>(registry)))
            .build();
        String input = """
            { "plugin": { "type": "io.kestra.core.plugins.serdes.PluginDeserializerTest.TestPlugin"} }
            """;

        // When
        String identifier = TestPlugin.class.getCanonicalName();
        Mockito
            .when(registry.findClassByIdentifier(identifier))
            .thenAnswer((Answer<Class<? extends Plugin>>) invocation -> TestPlugin.class);

        TestPluginHolder deserialized = om.readValue(input, TestPluginHolder.class);
        // Then
        assertThat(TestPlugin.class.getCanonicalName()).isEqualTo(deserialized.plugin().getType());
        Mockito.verify(registry, Mockito.times(1)).isVersioningSupported();
        Mockito.verify(registry, Mockito.times(1)).findClassByIdentifier(identifier);
    }

    @Test
    void shouldFailedDeserializePluginGivenInvalidType() {
        // Given
        ObjectMapper om = JsonMapper.builder()
            .addModule(new SimpleModule().addDeserializer(Plugin.class, new PluginDeserializer<>(registry)))
            .build();
        String input = """
            { "plugin": { "type": "io.kestra.core.plugins.serdes.Unknown"} }
            """;

        // When
        InvalidTypeIdException exception = Assertions.assertThrows(InvalidTypeIdException.class, () ->
        {
            om.readValue(input, TestPluginHolder.class);
        });

        // Then
        assertThat("io.kestra.core.plugins.serdes.Unknown").isEqualTo(exception.getTypeId());
    }

    @Test
    void shouldReturnNullPluginIdentifierGivenNullType() {
        // Jackson 3's StringNode disallows a null value (unlike Jackson 2's TextNode); NullNode is the correct
        // representation of a JSON null here.
        assertThat(PluginDeserializer.extractPluginRawIdentifier(NullNode.getInstance(), true)).isNull();
    }

    @Test
    void shouldReturnNullPluginIdentifierGivenEmptyType() {
        assertThat(PluginDeserializer.extractPluginRawIdentifier(new StringNode(""), true)).isNull();
    }

    @Test
    void shouldReturnTypeWithVersionGivenSupportedVersionTrue() {
        ObjectNode jsonNodes = new ObjectNode(JsonMapper.builder().build().getNodeFactory());
        jsonNodes.set("type", new StringNode("io.kestra.core.plugins.serdes.Unknown"));
        jsonNodes.set("version", new StringNode("1.0.0"));
        assertThat(PluginDeserializer.extractPluginRawIdentifier(jsonNodes, true)).isEqualTo("io.kestra.core.plugins.serdes.Unknown:1.0.0");
    }

    @Test
    void shouldReturnTypeWithVersionGivenSupportedVersionFalse() {
        ObjectNode jsonNodes = new ObjectNode(JsonMapper.builder().build().getNodeFactory());
        jsonNodes.set("type", new StringNode("io.kestra.core.plugins.serdes.Unknown"));
        jsonNodes.set("version", new StringNode("1.0.0"));
        assertThat(PluginDeserializer.extractPluginRawIdentifier(jsonNodes, false)).isEqualTo("io.kestra.core.plugins.serdes.Unknown");
    }

    public record TestPluginHolder(Plugin plugin) {
    }

    public record TestPlugin(String type) implements Plugin {
    }
}
