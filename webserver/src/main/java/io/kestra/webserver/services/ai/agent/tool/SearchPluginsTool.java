package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool searching the installed plugin types by keyword and kind, matching on the
 * fully-qualified class name and on the {@code @Schema} title. Deprecated types are skipped unless asked for.
 */
@Singleton
public class SearchPluginsTool implements McpPlatformTool {
    private static final int PAGE_SIZE = 50;

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

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "search_plugins",
        value = "Search the plugin types installed on this Kestra instance (tasks, triggers, task runners...) by keyword and kind, " + PAGE_SIZE + " per page sorted by type. "
            + "Read-only; use this to find the right plugin type before fetching its schema with get_plugin_schema or its documentation with read_plugin_documentation. Deprecated types are left out unless `includeDeprecated` is true. "
            + "Returns an object { plugins, total, page } where `plugins` is an array of { type, kind, title, pluginTitle, deprecated } (empty when nothing matches): `type` is the fully-qualified plugin class, "
            + "`kind` is TASK, TRIGGER, TASK_RUNNER, STORAGE, SECRET or another plugin kind, `title` may be null and `pluginTitle` names the plugin the type ships in. "
            + "`total` is the number of all matching types, which can exceed the length of `plugins`; pass `page` to read the next ones."
    )
    public Result searchPlugins(
        @P(name = "query", value = "Keyword matched case-insensitively against the plugin type name and title; omit to list every type of the given kind", required = false) String query,
        @P(name = "kind", value = "Only return plugin types of this kind, e.g. TASK or TRIGGER; omit for every kind", required = false) PluginKind kind,
        @P(name = "includeDeprecated", value = "Whether deprecated plugin types are included; omit to leave them out", required = false) Boolean includeDeprecated,
        @P(name = "page", value = "The 1-based page of plugin types to read; omit for the first page", required = false) Integer page) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        boolean withDeprecated = Boolean.TRUE.equals(includeDeprecated);
        int pageNumber = ToolPages.pageNumber(page);

        Map<String, PluginMatch> matches = new TreeMap<>();
        for (RegisteredPlugin plugin : pluginRegistry.plugins()) {
            for (Map.Entry<String, List<Class>> group : plugin.allClassGrouped().entrySet()) {
                Optional<PluginKind> groupKind = PluginKind.ofGroup(group.getKey());
                if (groupKind.isEmpty() || (kind != null && kind != groupKind.get())) {
                    continue;
                }
                for (Class<?> pluginClass : group.getValue()) {
                    boolean deprecated = isDeprecated(pluginClass);
                    if (deprecated && !withDeprecated) {
                        continue;
                    }
                    String title = title(pluginClass);
                    if (matches(pluginClass.getName(), title, needle)) {
                        matches.putIfAbsent(
                            pluginClass.getName(),
                            new PluginMatch(pluginClass.getName(), groupKind.get(), title, plugin.title(), deprecated)
                        );
                    }
                }
            }
        }

        ArrayListTotal<PluginMatch> pageOfMatches = ArrayListTotal.of(PageableUtils.from(pageNumber, PAGE_SIZE), List.copyOf(matches.values()));
        return new Result(List.copyOf(pageOfMatches), pageOfMatches.getTotal(), pageNumber);
    }

    private static boolean isDeprecated(final Class<?> pluginClass) {
        Schema schema = pluginClass.getDeclaredAnnotation(Schema.class);
        return pluginClass.isAnnotationPresent(Deprecated.class) || (schema != null && schema.deprecated());
    }

    private static String title(final Class<?> pluginClass) {
        Schema schema = pluginClass.getDeclaredAnnotation(Schema.class);
        return schema == null || schema.title().isBlank() ? null : schema.title();
    }

    private static boolean matches(final String type, final String title, final String needle) {
        return type.toLowerCase(Locale.ROOT).contains(needle)
            || (title != null && title.toLowerCase(Locale.ROOT).contains(needle));
    }

    /**
     * One page of the plugin types matching the search.
     *
     * @param plugins the types of the requested page, empty when nothing matches or the page is past the end
     * @param total the number of all matching types
     * @param page the 1-based page that was read
     */
    public record Result(List<PluginMatch> plugins, long total, int page) {
    }

    /**
     * A single matching plugin type.
     *
     * @param type the fully-qualified plugin class name
     * @param kind the kind of plugin type
     * @param title the plugin type's title, or null when it has none
     * @param pluginTitle the title of the plugin the type ships in
     * @param deprecated whether the type is deprecated
     */
    public record PluginMatch(String type, PluginKind kind, String title, String pluginTitle, boolean deprecated) {
    }
}
