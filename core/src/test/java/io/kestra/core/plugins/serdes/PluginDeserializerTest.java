package io.kestra.core.plugins.serdes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.TextNode;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class PluginDeserializerTest {

    @Mock
    private PluginRegistry registry;

    @BeforeEach
    void beforeEach() {
        PluginDeserializer.setPluginRegistry(registry);
    }

    @Test
    void shouldSucceededDeserializePluginGivenValidType() throws JsonProcessingException {
        // Given
        ObjectMapper om = new ObjectMapper()
            .registerModule(new SimpleModule().addDeserializer(Plugin.class, new PluginDeserializer<>()));
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
        Assertions.assertEquals(TestPlugin.class.getCanonicalName(), deserialized.plugin().getType());
        Mockito.verify(registry, Mockito.only()).findClassByIdentifier(identifier);
    }

    @Test
    void shouldFailedDeserializePluginGivenInvalidType() {
        // Given
        ObjectMapper om = new ObjectMapper()
            .registerModule(new SimpleModule().addDeserializer(Plugin.class, new PluginDeserializer<>()));
        String input = """
            { "plugin": { "type": "io.kestra.core.plugins.serdes.Unknown"} }
            """;

        // When
        InvalidTypeIdException exception = Assertions.assertThrows(InvalidTypeIdException.class, () -> {
            om.readValue(input, TestPluginHolder.class);
        });

        // Then
        Assertions.assertEquals("io.kestra.core.plugins.serdes.Unknown", exception.getTypeId());
    }

    @Test
    void shouldReturnNullPluginIdentifierGivenNullType() {
        Assertions.assertNull(PluginDeserializer.extractPluginRawIdentifier(new TextNode(null)));
    }

    @Test
    void shouldReturnNullPluginIdentifierGivenEmptyType() {
        Assertions.assertNull(PluginDeserializer.extractPluginRawIdentifier(new TextNode("")));
    }

    public record TestPluginHolder(Plugin plugin) {
    }

    public record TestPlugin(String type) implements Plugin {
    }
}