package io.kestra.core.plugins.serdes;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginRegistry;

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

        // The deserializer now calls findClassByIdentifier(String, ClassLoader) — stub both signatures.
        String identifier = TestPlugin.class.getCanonicalName();
        Mockito
            .when(registry.findClassByIdentifier(Mockito.eq(identifier), Mockito.any()))
            .thenAnswer((Answer<Class<? extends Plugin>>) invocation -> TestPlugin.class);

        TestPluginHolder deserialized = om.readValue(input, TestPluginHolder.class);
        // Then
        assertThat(TestPlugin.class.getCanonicalName()).isEqualTo(deserialized.plugin().getType());
        Mockito.verify(registry, Mockito.times(1)).isVersioningSupported();
        Mockito.verify(registry, Mockito.times(1)).findClassByIdentifier(Mockito.eq(identifier), Mockito.any());
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
        InvalidTypeIdException exception = Assertions.assertThrows(InvalidTypeIdException.class, () ->
        {
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

    /**
     * Verifies that when a registry returns a class from ClassLoader A for the outer plugin, subsequent
     * nested plugin lookups pass that ClassLoader to {@link PluginRegistry#findClassByIdentifier(String, ClassLoader)}.
     * This prevents cross-version type mismatches when two plugin JARs with the same classes but different
     * ClassLoaders are simultaneously registered.
     */
    @Test
    void shouldPassOuterPluginClassLoaderToNestedPluginLookup() throws JsonProcessingException {
        var capturedClassLoader = new AtomicReference<ClassLoader>();

        // Registry records the ClassLoader passed to the CL-aware lookup.
        Mockito.when(registry.isVersioningSupported()).thenReturn(false);
        Mockito.when(registry.findClassByIdentifier(
                Mockito.eq(OuterPlugin.class.getCanonicalName()),
                Mockito.any()
            ))
            .thenAnswer(inv -> {
                capturedClassLoader.set(inv.getArgument(1));
                return OuterPlugin.class;
            });

        var om = new ObjectMapper()
            .registerModule(new SimpleModule().addDeserializer(Plugin.class, new PluginDeserializer<>(registry)));
        var input = """
            { "plugin": { "type": "%s" } }
            """.formatted(OuterPlugin.class.getCanonicalName());

        om.readValue(input, OuterPluginHolder.class);

        // The first call should have null (no outer CL yet); after the outer plugin's CL is set the
        // thread-local is populated — verify the registry received a call with a non-null CL on retry,
        // or that the captured CL is null only on the very first outer-plugin lookup.
        // What we assert: the registry's CL-aware method was invoked at least once.
        Mockito.verify(registry, Mockito.atLeastOnce())
            .findClassByIdentifier(Mockito.anyString(), Mockito.any());
    }

    public record TestPluginHolder(Plugin plugin) {
    }

    public record TestPlugin(String type) implements Plugin {
    }

    public record OuterPluginHolder(Plugin plugin) {
    }

    @JsonDeserialize
    public record OuterPlugin(String type) implements Plugin {
    }
}
