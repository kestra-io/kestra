package io.kestra.webserver.services.ai.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.docs.ClassPluginDocumentation;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.utils.ListUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool returning the JSON schema of a single plugin type, restricted to the properties the plugin
 * itself declares. The properties inherited from its base class, and the definitions they reference, are excluded
 * to keep the payload small enough for a model to read.
 */
@Singleton
public class GetPluginSchemaTool implements AiPlatformTool {
    private static final Set<String> DOCUMENTATION_ONLY_KEYS = Set.of(
        "$schema", "$examples", "$metrics", "$deprecated", "$beta", "title", "description"
    );

    private final PluginRegistry pluginRegistry;
    private final JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    public GetPluginSchemaTool(final PluginRegistry pluginRegistry, final JsonSchemaGenerator jsonSchemaGenerator) {
        this.pluginRegistry = pluginRegistry;
        this.jsonSchemaGenerator = jsonSchemaGenerator;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Tool(
        name = "get-plugin-schema",
        value = "Get the JSON schema of a Kestra plugin type: the properties it declares, their types, and its outputs. Read-only; use this to learn how to configure a task, trigger or other plugin before authoring flow YAML. "
            + "Returns an object { pluginType, title, description, properties, outputs, defs, examples } where `properties` and `outputs` are JSON schemas, `defs` holds the definitions they reference and `examples` is an array of { title, code } YAML snippets. "
            + "The properties common to every task (retry, timeout, runIf...) are not included."
    )
    public Result getPluginSchema(
        @P(name = "pluginType", value = "The fully-qualified plugin type, e.g. io.kestra.plugin.core.log.Log") String pluginType) {
        PluginClassAndMetadata<? extends Plugin> metadata = pluginRegistry.findMetadataByIdentifier(pluginType)
            .orElseThrow(() -> new IllegalArgumentException("Plugin type not found: '%s'".formatted(pluginType)));

        ClassPluginDocumentation<?> documentation = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, null, false);

        return new Result(
            pluginType,
            documentation.getDocDescription(),
            documentation.getDocBody(),
            propertiesSchemaOf(documentation),
            documentation.getOutputsSchema(),
            documentation.getDefs(),
            ListUtils.emptyOnNull(documentation.getDocExamples())
                .stream()
                .map(example -> new Example(example.getTitle(), example.getTask()))
                .toList()
        );
    }

    /**
     * Returns the plugin's properties schema without the documentation-only keys. {@link ClassPluginDocumentation}
     * instances are cached and shared with the documentation API, so the map is copied before pruning.
     */
    private static Map<String, Object> propertiesSchemaOf(final ClassPluginDocumentation<?> documentation) {
        Map<String, Object> properties = new LinkedHashMap<>(documentation.getPropertiesSchema());
        properties.keySet().removeAll(DOCUMENTATION_ONLY_KEYS);

        return properties;
    }

    public record Result(
        String pluginType,
        String title,
        String description,
        Map<String, Object> properties,
        Map<String, Object> outputs,
        Map<String, Object> defs,
        List<Example> examples
    ) {
    }

    public record Example(String title, String code) {
    }
}
