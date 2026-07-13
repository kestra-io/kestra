package io.kestra.webserver.services.ai.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool searching the installed plugin classes by keyword, matching on the
 * fully-qualified class name and on the {@code @Schema} title. Deprecated plugins are skipped.
 */
@Singleton
public class SearchPluginsTool implements AiPlatformTool {
    private static final int MAX_RESULTS = 50;

    private final PluginRegistry pluginRegistry;

    @Inject
    public SearchPluginsTool(final PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
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
        name = "search-plugins",
        value = "Search the installed Kestra plugins (tasks, triggers, conditions...) by keyword, matching plugin type names and titles (at most 50 results). Read-only; use this to find the right plugin type before fetching its schema with get-plugin-schema."
    )
    public String searchPlugins(
        @P(name = "query", value = "Keyword matched case-insensitively against the plugin class name and title") String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();

        // LinkedHashMap keyed by type deduplicates classes registered under several groups while keeping order.
        Map<String, String> matches = new LinkedHashMap<>();
        for (RegisteredPlugin plugin : pluginRegistry.plugins()) {
            for (List<Class> classes : plugin.allClassGrouped().values()) {
                for (Class<?> pluginClass : classes) {
                    if (matches.size() >= MAX_RESULTS) {
                        return format(matches, needle);
                    }
                    Schema schema = pluginClass.getDeclaredAnnotation(Schema.class);
                    if (schema != null && schema.deprecated()) {
                        continue;
                    }
                    String title = Optional.ofNullable(schema).map(Schema::title).orElse(null);
                    if (matches(pluginClass.getName(), title, needle)) {
                        matches.put(pluginClass.getName(), title);
                    }
                }
            }
        }
        return format(matches, needle);
    }

    private static boolean matches(final String type, final String title, final String needle) {
        return type.toLowerCase(Locale.ROOT).contains(needle)
            || (title != null && title.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static String format(final Map<String, String> matches, final String needle) {
        if (matches.isEmpty()) {
            return "No plugins found matching '" + needle + "'.";
        }
        StringBuilder out = new StringBuilder();
        matches.forEach((type, title) ->
        {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(type);
            if (title != null && !title.isBlank()) {
                out.append(" — ").append(title);
            }
        });
        return out.toString();
    }
}
