package io.kestra.core.plugins.serdes;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
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

    private volatile PluginRegistry pluginRegistry;

    /**
     * Creates a new {@link PluginDeserializer} instance.
     */
    public PluginDeserializer() {
    }

    /**
     * Creates a new {@link PluginDeserializer} instance.
     *
     * @param pluginRegistry The {@link PluginRegistry}.
     */
    PluginDeserializer(final PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
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

    private void checkState() {
        if (pluginRegistry == null) {
            try {
                // By default, if no plugin-registry is configured retrieve
                // the one configured from the static Kestra's context.
                pluginRegistry = KestraContext.getContext().getPluginRegistry();
            } catch (IllegalStateException ignore) {
                // This error can only happen if the KestraContext is not initialized (i.e. in unit tests).
                log.error("No plugin registry was initialized. Use default implementation.");
                pluginRegistry = DefaultPluginRegistry.getOrCreate();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private T fromObjectNode(JsonParser jp,
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

            T pluginValue = (T) jp.getCodec().treeToValue(node, pluginType);

            try {
                nullPropertyToNullWrapperProperty(pluginValue, pluginType);
            } catch (IllegalAccessException e) {
                // Silent catch, we couldn't replace null properties
            }
            return pluginValue;
        }

        // should not happen.
        log.warn("Failed get plugin type from JsonNode");
        return null;
    }

    private void nullPropertyToNullWrapperProperty(Object object, Class<?> type) throws IllegalAccessException {
        if (object == null) {
            return;
        }

        for(Field field: type.getDeclaredFields()) {
            if (!field.trySetAccessible()) {
                continue;
            }

            Object fieldValue = field.get(object);
            if (field.getType() == Property.class) {
                if (fieldValue == null) {
                    field.set(object, new Property<>());
                }
            } else if (fieldValue != object) {
                nullPropertyToNullWrapperProperty(fieldValue, field.getType());
            }
        }
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
