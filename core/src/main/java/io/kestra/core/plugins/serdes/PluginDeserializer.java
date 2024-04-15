package io.kestra.core.plugins.serdes;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Specific {@link JsonDeserializer} for deserializing classes that implements the {@link Plugin} interface.
 * <p>
 * The {@link PluginDeserializer} uses the {@link PluginRegistry} to found the plugin class corresponding to
 * a plugin type.
 */
public final class PluginDeserializer<T extends Plugin> extends JsonDeserializer<T> {

    private static final Logger log = LoggerFactory.getLogger(PluginDeserializer.class);

    private static final String TYPE = "type";

    private volatile static PluginRegistry pluginRegistry;

    /**
     * Creates a new {@link PluginDeserializer} instance.
     */
    public PluginDeserializer() {
    }

    /**
     * Sets the static {@link PluginRegistry} to be used by the deserializer.
     *
     * @param pluginRegistry the {@link PluginRegistry}.
     */
    public static void setPluginRegistry(final PluginRegistry pluginRegistry) {
        Objects.requireNonNull(pluginRegistry, "PluginRegistry cannot be null");
        PluginDeserializer.pluginRegistry = pluginRegistry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        checkState();
        JsonNode node = parser.readValueAsTree();
        if (node.isObject()) {
            return fromObjectNode(parser, node, context);
        } else {
            return null;
        }
    }

    private static void checkState() {
        if (pluginRegistry == null) throw new IllegalStateException("PluginRegistry not initialized.");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Plugin> T fromObjectNode(JsonParser jp,
                                                       JsonNode node,
                                                       DeserializationContext context) throws IOException {
        Class<? extends Plugin> pluginType = null;

        final String identifier = extractPluginRawIdentifier(node);
        if (identifier != null) {
            log.trace("Looking for Plugin for: {}",
                identifier
            );
            pluginType = pluginRegistry.findClassByIdentifier(identifier);
        }

        if (pluginType == null) {
            String type = Optional.ofNullable(identifier).orElse("<null>");
            throwInvalidTypeException(context, type);
        } else if (Plugin.class.isAssignableFrom(pluginType)) {
            log.trace("Read plugin for: {}",
                pluginType.getName()
            );
            // Note that if the provided plugin is not annotated with `@JsonDeserialize()` then
            // the following method will end up to a StackOverflowException as the `PluginDeserializer` will be re-invoked.
            return (T) jp.getCodec().treeToValue(node, pluginType);
        }

        // should not happen.
        log.warn("Failed get plugin type from JsonNode");
        return null;
    }

    private static void throwInvalidTypeException(final DeserializationContext context,
                                                  final String type) throws JsonMappingException {
        throw context.invalidTypeIdException(
            context.constructType(Plugin.class),
            type,
            "No plugin registered for the defined type: '" + type + "'"
        );
    }

    static String extractPluginRawIdentifier(final JsonNode node) {
        JsonNode type = node.get(TYPE);
        if (type == null || type.textValue().isEmpty()) {
            return null;
        }
        return type.textValue();
    }
}
