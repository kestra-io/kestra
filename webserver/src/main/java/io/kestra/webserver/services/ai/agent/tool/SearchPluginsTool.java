package io.kestra.webserver.services.ai.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;

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
        value = "Search the installed Kestra plugins (tasks, triggers, conditions...) by keyword, matching plugin type names and titles (at most 50 results). Read-only; use this to find the right plugin type before fetching its schema with get-plugin-schema. "
            + "Returns an object { plugins } where `plugins` is an array of { type, title } (empty when nothing matches); `type` is the fully-qualified plugin class and `title` may be null."
    )
    public Result searchPlugins(
        @P(name = "query", value = "Keyword matched case-insensitively against the plugin class name and title") String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();

        // LinkedHashMap keyed by type deduplicates classes registered under several groups while keeping order.
        Map<String, String> matches = new LinkedHashMap<>();
        for (RegisteredPlugin plugin : pluginRegistry.plugins()) {
            for (List<Class> classes : plugin.allClassGrouped().values()) {
                for (Class<?> pluginClass : classes) {
                    if (matches.size() >= MAX_RESULTS) {
                        return toResult(matches);
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
        return toResult(matches);
    }

    private static boolean matches(final String type, final String title, final String needle) {
        return type.toLowerCase(Locale.ROOT).contains(needle)
            || (title != null && title.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static Result toResult(final Map<String, String> matches) {
        return new Result(
            matches.entrySet().stream()
                .map(entry -> new PluginMatch(entry.getKey(), entry.getValue()))
                .toList()
        );
    }

    /**
     * The plugin types matching the search keyword.
     *
     * @param plugins one entry per matching plugin type, empty when nothing matches
     */
    public record Result(List<PluginMatch> plugins) {
    }

    /**
     * A single matching plugin type.
     *
     * @param type the fully-qualified plugin class name
     * @param title the plugin title, or null when it has none
     */
    public record PluginMatch(String type, String title) {
    }
}
