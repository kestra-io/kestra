package io.kestra.core.plugins.serdes;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.TypeFactory;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.context.exceptions.NoSuchBeanException;
import lombok.extern.slf4j.Slf4j;

/**
 * Specific {@link JsonDeserializer} for deserializing classes that implements the {@link Plugin} interface.
 * <p>
 * The {@link PluginDeserializer} uses the {@link PluginRegistry} to found the plugin class corresponding to
 * a plugin type.
 */
@Slf4j
public class PluginDeserializer<T extends Plugin> extends JsonDeserializer<T> {

    private static final String TYPE = "type";
    private static final String VERSION = "version";

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
            } catch (IllegalStateException | NoSuchBeanException | NullPointerException ignore) {
                // This error can only happen if the KestraContext is not initialized (i.e. in unit tests).
                // NullPointerException included because a KestraContext.Initializer left behind by a closed
                // context has a null application context, so getPluginRegistry() throws that instead.
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

        final String identifier = extractPluginRawIdentifier(node, pluginRegistry.isVersioningSupported());
        if (identifier != null) {
            log.trace(
                "Looking for Plugin for: {}",
                identifier
            );
            pluginType = pluginRegistry.findClassByIdentifier(identifier);

            if (pluginType == null) {
                pluginType = fallbackClass();
            }
        }

        if (pluginType == null) {
            String type = Optional.ofNullable(identifier).orElse("<null>");
            throwInvalidTypeException(context, type);
        } else if (Plugin.class.isAssignableFrom(pluginType)) {
            log.trace(
                "Read plugin for: {}",
                pluginType.getName()
            );

            if (DataChart.class.isAssignableFrom(pluginType)) {
                final Class<? extends Plugin> dataFilterClass = pluginRegistry.findClassByIdentifier(extractPluginRawIdentifier(node.get("data"), pluginRegistry.isVersioningSupported()));
                ParameterizedType genericDataFilterClass = (ParameterizedType) dataFilterClass.getGenericSuperclass();
                Type dataFieldsEnum = genericDataFilterClass.getActualTypeArguments()[0];
                TypeFactory typeFactory = JacksonMapper.ofJson(true).getTypeFactory();
                Type chartAwareColumnDescriptorClass = ((ParameterizedType) ((WildcardType) ((ParameterizedType) ((TypeVariable<?>)
                // DataChart generic class
                ((ParameterizedType) pluginType.getGenericSuperclass())
                    // DataFilter generic class
                    .getActualTypeArguments()[1]).getBounds()[0]
                // ColumnDescriptor implementation class
                ).getActualTypeArguments()[1]).getUpperBounds()[0]).getRawType();

                return JacksonMapper.ofJson(true).convertValue(
                    node, typeFactory.constructParametricType(
                        pluginType,
                        typeFactory.constructType(dataFieldsEnum),
                        typeFactory.constructParametricType(
                            dataFilterClass,
                            typeFactory.constructParametricType((Class<?>) chartAwareColumnDescriptorClass, (Class<?>) dataFieldsEnum)
                        )
                    )
                );
            }

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

    static String extractPluginRawIdentifier(final JsonNode node, final boolean isVersioningSupported) {
        String type = Optional.ofNullable(node.get(TYPE)).map(JsonNode::textValue).orElse(null);
        String version = Optional.ofNullable(node.get(VERSION)).map(JsonNode::asText).orElse(null);

        return rawIdentifier(type, version, isVersioningSupported);
    }

    /**
     * Builds the plugin registry identifier from an already-extracted type and version.
     * <p>
     * Kept Jackson-version-neutral so {@link Jackson3PluginDeserializer}, which reads a
     * {@code tools.jackson} tree, applies exactly the same identifier convention.
     *
     * @param type                 The plugin type, may be {@code null} or empty.
     * @param version              The plugin version, may be {@code null} or empty.
     * @param isVersioningSupported Whether the registry resolves versioned identifiers.
     * @return The raw identifier, or {@code null} if no type was provided.
     */
    static String rawIdentifier(final String type, final String version, final boolean isVersioningSupported) {
        if (type == null || type.isEmpty()) {
            return null;
        }

        return isVersioningSupported && version != null && !version.isEmpty() ? type + ":" + version : type;
    }

    protected Class<? extends Plugin> fallbackClass() {
        return null;
    }
}
