package io.kestra.core.plugins.serdes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PluginDeserializerTest {

    @Mock
    private PluginRegistry registry;

    @Test
    void shouldSucceededDeserializePluginGivenValidType() throws JsonProcessingException {
        // Given
        ObjectMapper om = new ObjectMapper()
            .registerModule(new SimpleModule().addDeserializer(Plugin.class, new PluginDeserializer<>(registry)));
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
        ObjectMapper om = new ObjectMapper()
            .registerModule(new SimpleModule().addDeserializer(Plugin.class, new PluginDeserializer<>(registry)));
        String input = """
            { "plugin": { "type": "io.kestra.core.plugins.serdes.Unknown"} }
            """;

        // When
        InvalidTypeIdException exception = Assertions.assertThrows(InvalidTypeIdException.class, () -> {
            om.readValue(input, TestPluginHolder.class);
        });

        // Then
        assertThat("io.kestra.core.plugins.serdes.Unknown").isEqualTo(exception.getTypeId());
    }

    @Test
    void shouldReturnNullPluginIdentifierGivenNullType() {
        assertThat(PluginDeserializer.extractPluginRawIdentifier(new TextNode(null), true)).isNull();
    }

    @Test
    void shouldReturnNullPluginIdentifierGivenEmptyType() {
        assertThat(PluginDeserializer.extractPluginRawIdentifier(new TextNode(""), true)).isNull();
    }

    @Test
    void shouldReturnTypeWithVersionGivenSupportedVersionTrue() {
        ObjectNode jsonNodes = new ObjectNode(new ObjectMapper().getNodeFactory());
        jsonNodes.set("type", new TextNode("io.kestra.core.plugins.serdes.Unknown"));
        jsonNodes.set("version", new TextNode("1.0.0"));
        assertThat(PluginDeserializer.extractPluginRawIdentifier(jsonNodes, true)).isEqualTo("io.kestra.core.plugins.serdes.Unknown:1.0.0");
    }

    @Test
    void shouldReturnTypeWithVersionGivenSupportedVersionFalse() {
        ObjectNode jsonNodes = new ObjectNode(new ObjectMapper().getNodeFactory());
        jsonNodes.set("type", new TextNode("io.kestra.core.plugins.serdes.Unknown"));
        jsonNodes.set("version", new TextNode("1.0.0"));
        assertThat(PluginDeserializer.extractPluginRawIdentifier(jsonNodes, false)).isEqualTo("io.kestra.core.plugins.serdes.Unknown");
    }

    public record TestPluginHolder(Plugin plugin) {
    }

    public record TestPlugin(String type) implements Plugin {
    }
}
