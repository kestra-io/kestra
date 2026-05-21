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
 * <p>
 * When multiple versions of the same plugin JAR coexist in the registry (e.g. v1.12.3 and v1.12.4), the
 * registry may return classes from different ClassLoaders for the outer Task and its nested
 * {@code AdditionalPlugin} fields. This causes a {@link IllegalArgumentException} at Jackson's
 * reflection-based field assignment because {@code ModelProvider_CLv3.isAssignableFrom(GoogleGemini_CLv4)}
 * is {@code false}. A thread-local tracks the ClassLoader of the first (outermost) plugin resolved in a
 * deserialization chain so that all nested plugin lookups prefer the same ClassLoader.
 */
@Slf4j
public class PluginDeserializer<T extends Plugin> extends JsonDeserializer<T> {

    private static final String TYPE = "type";
    private static final String VERSION = "version";

    /**
     * Tracks the ClassLoader of the most-recently-entered plugin-JAR class being deserialized
     * on the current thread. Acts as a push/pop stack: each time deserialization enters a plugin
     * class from a different ClassLoader the ThreadLocal is updated and restored on exit.
     * This ensures that nested {@code AdditionalPlugin} lookups (e.g. a {@code ModelProvider}
     * field inside a task) are resolved from the same ClassLoader as the enclosing task,
     * even when multiple versions of the same plugin JAR coexist in the registry.
     */
    private static final ThreadLocal<ClassLoader> CURRENT_PLUGIN_CLASS_LOADER = new ThreadLocal<>();

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
            } catch (IllegalStateException | NoSuchBeanException ignore) {
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

        final String identifier = extractPluginRawIdentifier(node, pluginRegistry.isVersioningSupported());
        if (identifier != null) {
            log.trace(
                "Looking for Plugin for: {}",
                identifier
            );
            // Prefer the ClassLoader of the outermost plugin in the current deserialization chain
            // to avoid ClassLoader identity mismatches when multiple versions of the same plugin JAR coexist.
            pluginType = pluginRegistry.findClassByIdentifier(identifier, CURRENT_PLUGIN_CLASS_LOADER.get());

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
                final Class<? extends Plugin> dataFilterClass = pluginRegistry.findClassByIdentifier(
                    extractPluginRawIdentifier(node.get("data"), pluginRegistry.isVersioningSupported()),
                    CURRENT_PLUGIN_CLASS_LOADER.get()
                );
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

            // Push the resolved class's ClassLoader so that nested AdditionalPlugin lookups
            // (e.g. a ModelProvider field inside a task) prefer the same ClassLoader.
            // This is a push/pop: we restore the previous value on exit so that sibling tasks
            // inside a core container (Parallel, Sequential, …) each get their own CL context.
            ClassLoader resolvedCL = pluginType.getClassLoader();
            ClassLoader previousCL = CURRENT_PLUGIN_CLASS_LOADER.get();
            boolean pushCL = resolvedCL != null && resolvedCL != previousCL;
            if (pushCL) {
                CURRENT_PLUGIN_CLASS_LOADER.set(resolvedCL);
            }
            try {
                // Note that if the provided plugin is not annotated with `@JsonDeserialize()` then
                // the following method will end up to a StackOverflowException as the `PluginDeserializer` will be re-invoked.
                return (T) jp.getCodec().treeToValue(node, pluginType);
            } finally {
                if (pushCL) {
                    if (previousCL == null) {
                        CURRENT_PLUGIN_CLASS_LOADER.remove();
                    } else {
                        CURRENT_PLUGIN_CLASS_LOADER.set(previousCL);
                    }
                }
            }
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

        if (type == null || type.isEmpty()) {
            return null;
        }

        return isVersioningSupported && version != null && !version.isEmpty() ? type + ":" + version : type;
    }

    protected Class<? extends Plugin> fallbackClass() {
        return null;
    }
}
