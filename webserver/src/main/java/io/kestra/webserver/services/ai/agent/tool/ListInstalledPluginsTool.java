package io.kestra.webserver.services.ai.agent.tool;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.docs.Plugin;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.ListUtils;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.Searchable;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static java.util.function.Predicate.not;

/**
 * Read-only agent tool listing the installed plugins, one compact line per plugin artifact and version,
 * filtered with the same in-memory predicates as the plugin REST endpoint.
 */
@Singleton
public class ListInstalledPluginsTool implements McpPlatformTool {
    private static final int PAGE_SIZE = 50;
    private static final Comparator<RegisteredPlugin> BY_ARTIFACT_THEN_VERSION = Comparator
        .comparing(RegisteredPlugin::name, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(RegisteredPlugin::version, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final PluginRegistry pluginRegistry;
    private final Searchable<Plugin> pluginSearchable;

    @Inject
    public ListInstalledPluginsTool(final PluginRegistry pluginRegistry, @Named("PLUGIN") final Searchable<Plugin> pluginSearchable) {
        this.pluginRegistry = pluginRegistry;
        this.pluginSearchable = pluginSearchable;
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
        name = "list_installed_plugins",
        value = "List the plugins installed on this Kestra instance matching the given per-field filters, " + PAGE_SIZE + " per page sorted by artifactId then version: "
            + "QUERY matches a substring of the plugin title, group or artifactId, and ARTIFACT_ID narrows to the given comma-separated artifactIds. "
            + "Read-only; use this to see which plugins and versions are installed before searching their types with search_plugins. "
            + "Returns an object { plugins, total, page } where `plugins` is an array of { artifactId, group, title, version, license, description, types } (empty when nothing matches): "
            + "`types` maps each kind of plugin type (TASK, TRIGGER, TASK_RUNNER...) to how many of them the plugin ships, and `version` is null for the plugins bundled with Kestra. "
            + "`total` is the number of all matching plugins, which can exceed the length of `plugins`; pass `page` to read the next ones."
    )
    public Result listInstalledPlugins(
        @QueryFilterFormat(QueryFilter.Resource.PLUGIN) List<QueryFilter> filters,
        @P(name = "page", value = "The 1-based page of plugins to read; omit for the first page", required = false) Integer page) {
        List<QueryFilter> effective = ListUtils.emptyOnNull(filters).stream().map(ListInstalledPluginsTool::withListValue).toList();
        int pageNumber = ToolPages.pageNumber(page);

        List<RegisteredPlugin> matching = pluginRegistry.plugins().stream()
            .filter(plugin -> pluginSearchable.matches(Plugin.of(plugin, null), effective))
            .sorted(BY_ARTIFACT_THEN_VERSION)
            .toList();
        ArrayListTotal<RegisteredPlugin> pageOfPlugins = ArrayListTotal.of(PageableUtils.from(pageNumber, PAGE_SIZE), matching);

        return new Result(
            pageOfPlugins.stream().map(ListInstalledPluginsTool::toSummary).toList(),
            pageOfPlugins.getTotal(),
            pageNumber
        );
    }

    /** The model sends every filter value as a string, while the IN and NOT_IN predicates expect a list. */
    private static QueryFilter withListValue(final QueryFilter filter) {
        boolean listOperator = filter.operation() == QueryFilter.Op.IN || filter.operation() == QueryFilter.Op.NOT_IN;
        if (!filter.isLeaf() || !listOperator || !(filter.value() instanceof String value)) {
            return filter;
        }
        List<String> values = Arrays.stream(value.split(",")).map(String::trim).filter(not(String::isEmpty)).toList();
        return new QueryFilter(filter.field(), filter.operation(), values, null, null);
    }

    private static PluginSummary toSummary(final RegisteredPlugin plugin) {
        Map<PluginKind, Integer> types = new EnumMap<>(PluginKind.class);
        plugin.allClassGrouped().forEach(
            (group, classes) -> PluginKind.ofGroup(group)
                .filter(kind -> !classes.isEmpty())
                .ifPresent(kind -> types.put(kind, classes.size()))
        );
        return new PluginSummary(plugin.name(), plugin.group(), plugin.title(), plugin.version(), plugin.license(), plugin.description(), types);
    }

    /**
     * One page of the installed plugins matching the filters.
     *
     * @param plugins the plugins of the requested page, empty when nothing matches or the page is past the end
     * @param total the number of all matching plugins
     * @param page the 1-based page that was read
     */
    public record Result(List<PluginSummary> plugins, long total, int page) {
    }

    /**
     * A single installed plugin artifact at one version.
     *
     * @param artifactId the Maven artifactId of the plugin, {@code core} for the plugins bundled with Kestra
     * @param version the plugin version, or null for the plugins bundled with Kestra
     * @param types how many plugin types of each kind the plugin ships
     */
    public record PluginSummary(String artifactId, String group, String title, String version, String license, String description, Map<PluginKind, Integer> types) {
    }
}
