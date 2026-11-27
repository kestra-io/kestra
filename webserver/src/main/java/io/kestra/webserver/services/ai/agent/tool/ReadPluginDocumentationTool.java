package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.docs.ClassPluginDocumentation;
import io.kestra.core.docs.DocumentationGenerator;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.core.plugins.PluginRegistry;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool rendering the Markdown documentation of a single plugin type, the same page the
 * UI shows, without its front matter.
 */
@Singleton
public class ReadPluginDocumentationTool implements McpPlatformTool {
    private static final String FRONT_MATTER_DELIMITER = "---";

    private final PluginRegistry pluginRegistry;
    private final JsonSchemaGenerator jsonSchemaGenerator;

    @Inject
    public ReadPluginDocumentationTool(final PluginRegistry pluginRegistry, final JsonSchemaGenerator jsonSchemaGenerator) {
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

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "read_plugin_documentation",
        value = "Read the documentation of a Kestra plugin type as Markdown: its description, YAML examples, properties, outputs and metrics, as shown on the plugin page of the UI. "
            + "Read-only; use this to learn how a task, trigger or other plugin is used and to start from a working example before authoring flow YAML; get_plugin_schema gives the same properties as a JSON schema. "
            + "Returns an object { pluginType, markdown } where `markdown` is the full documentation."
    )
    public Result readPluginDocumentation(
        @P(name = "pluginType", value = "The fully-qualified plugin type, e.g. io.kestra.plugin.core.log.Log") String pluginType) {
        PluginClassAndMetadata<? extends Plugin> metadata = pluginRegistry.findMetadataByIdentifier(pluginType)
            .orElseThrow(() -> new IllegalArgumentException("Plugin type not found: '%s'".formatted(pluginType)));

        return documentation(pluginType, metadata, null);
    }

    /**
     * Renders the documentation of an already resolved plugin type, without its front matter.
     *
     * @param version the plugin version to document, or null for the default one
     */
    protected Result documentation(
        final String pluginType, final PluginClassAndMetadata<? extends Plugin> metadata, @Nullable final String version) {
        return new Result(pluginType, withoutFrontMatter(render(metadata, version)));
    }

    private String render(final PluginClassAndMetadata<? extends Plugin> metadata, @Nullable final String version) {
        try {
            return DocumentationGenerator.render(ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, version, false));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String withoutFrontMatter(final String markdown) {
        if (!markdown.startsWith(FRONT_MATTER_DELIMITER)) {
            return markdown;
        }
        int end = markdown.indexOf("\n" + FRONT_MATTER_DELIMITER, FRONT_MATTER_DELIMITER.length());
        if (end == -1) {
            return markdown;
        }
        return markdown.substring(end + FRONT_MATTER_DELIMITER.length() + 1).stripLeading();
    }

    /**
     * The documentation of a single plugin type.
     *
     * @param markdown the documentation
     */
    public record Result(String pluginType, String markdown) {
    }
}
