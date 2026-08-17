package io.kestra.core.plugins.serdes;

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.context.exceptions.NoSuchBeanException;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson 3 counterpart of {@link PluginDeserializer}, used by the Micronaut-managed mapper that binds
 * HTTP request and response bodies.
 * <p>
 * Kestra's own {@link JacksonMapper} hub stays on Jackson 2, so both versions of this deserializer are
 * needed: a {@code @JsonDeserialize(using = ...)} annotation and a module registration are tied to one
 * Jackson major version, and Micronaut 5's Jackson-2 annotation compatibility layer covers only
 * {@code as=}/{@code builder=}, not {@code using=}.
 * <p>
 * The plugin identifier convention and registry lookup are shared with the Jackson 2 implementation via
 * {@link PluginDeserializer#rawIdentifier(String, String, boolean)} so the two cannot drift apart.
 *
 * @param <T> The plugin type.
 */
@Slf4j
public class Jackson3PluginDeserializer<T extends Plugin> extends ValueDeserializer<T> {

    private static final String TYPE = "type";
    private static final String VERSION = "version";

    private volatile PluginRegistry pluginRegistry;

    /**
     * Creates a new {@link Jackson3PluginDeserializer} instance.
     */
    public Jackson3PluginDeserializer() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T deserialize(tools.jackson.core.JsonParser parser, DeserializationContext context) {
        checkState();
        // Read the tree through the context rather than parser.readValueAsTree(): the latter goes via
        // parser.objectReadContext(), which builds a DeserializationContext with no parser assigned, so its
        // stream-read capabilities are null and any tree-level check against them throws NPE (Jackson 3 does
        // exactly that on a duplicate property). The context handed to a deserializer is always assigned.
        JsonNode node = context.readTree(parser);
        if (node.isObject()) {
            return fromObjectNode(node, context);
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
    private T fromObjectNode(JsonNode node, DeserializationContext context) {
        final String identifier = extractPluginRawIdentifier(node, pluginRegistry.isVersioningSupported());

        Class<? extends Plugin> pluginType = null;
        if (identifier != null) {
            log.trace("Looking for Plugin for: {}", identifier);
            pluginType = pluginRegistry.findClassByIdentifier(identifier);

            if (pluginType == null) {
                pluginType = fallbackClass();
            }
        }

        if (pluginType == null) {
            throw context.invalidTypeIdException(
                context.constructType(Plugin.class),
                Optional.ofNullable(identifier).orElse("<null>"),
                "No plugin registered for the defined type: '%s'".formatted(Optional.ofNullable(identifier).orElse("<null>"))
            );
        }

        if (!Plugin.class.isAssignableFrom(pluginType)) {
            // should not happen.
            log.warn("Failed get plugin type from JsonNode");
            return null;
        }

        log.trace("Read plugin for: {}", pluginType.getName());

        if (DataChart.class.isAssignableFrom(pluginType)) {
            // Resolving a DataChart's nested generics is intricate and lives in the Jackson 2 deserializer.
            // Charts only ever reach the API as a YAML string parsed by JacksonMapper, so delegate to the
            // hub instead of duplicating that logic here.
            try {
                return (T) JacksonMapper.ofJson(true).readValue(node.toString(), Chart.class);
            } catch (JsonProcessingException e) {
                throw context.instantiationException(pluginType, e);
            }
        }

        // Read through the context, which reuses its assigned parser's capabilities (see the note in
        // deserialize() on why parser.objectReadContext() must be avoided here).
        //
        // Note that if the provided plugin is not annotated with `@JsonDeserialize()` then
        // the following will end up in a StackOverflowException as this deserializer will be re-invoked.
        return (T) context.readTreeAsValue(node, pluginType);
    }

    private static String extractPluginRawIdentifier(final JsonNode node, final boolean isVersioningSupported) {
        // The defaulted overloads, not stringValue()/asString(): those throw JsonNodeException for a non-String
        // node, where Jackson 2's textValue()/asText() returned null. Without them a `"type": 123` would abort
        // the tree walk instead of reaching the invalid-type-id error below.
        String type = Optional.ofNullable(node.get(TYPE)).map(n -> n.stringValue(null)).orElse(null);
        String version = Optional.ofNullable(node.get(VERSION)).map(n -> n.asString(null)).orElse(null);

        return PluginDeserializer.rawIdentifier(type, version, isVersioningSupported);
    }

    protected Class<? extends Plugin> fallbackClass() {
        return null;
    }
}
