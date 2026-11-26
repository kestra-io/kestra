package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.flows.SourceSearchScope;
import io.kestra.webserver.models.flows.SourceSearchResult;
import io.kestra.webserver.services.SourceSearchService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool searching the YAML source of flows for a text or a regular expression.
 */
@Singleton
public class SearchFlowSourcesTool implements McpPlatformTool {
    private static final int MAX_FLOWS = 25;
    private static final int MAX_MATCHES_PER_FLOW = 5;

    private final SourceSearchService sourceSearchService;

    @Inject
    public SearchFlowSourcesTool(final SourceSearchService sourceSearchService) {
        this.sourceSearchService = sourceSearchService;
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
        name = "search_flow_sources",
        value = "Search the YAML source of Kestra flows for a text or a regular expression (" + MAX_FLOWS + " flows per page). Read-only; use this to find flows that use a plugin, a task type, an expression or any other text in their code. "
            + "To find flows by name or namespace, use list_flows instead. "
            + "Returns an object { flows, total, page } where `flows` is an array of { namespace, id, matches } and `matches` holds at most " + MAX_MATCHES_PER_FLOW + " { line, snippet } per flow, "
            + "with the matched text wrapped in [mark]...[/mark] in `snippet`. `total` is the number of all matching flows; when it exceeds what was read so far, request the next `page`."
    )
    public Result searchFlowSources(
        @P(name = "query", value = "The text to search for, or a regular expression when `regex` is true") String query,
        @P(name = "namespace", value = "Only search flows in this namespace and its child namespaces", required = false) String namespace,
        @P(name = "regex", value = "Whether `query` is a regular expression; default false", required = false) Boolean regex,
        @P(name = "caseSensitive", value = "Whether the match must have the same case; default false", required = false) Boolean caseSensitive,
        @P(name = "wholeWord", value = "Whether the match must be a whole word; default false", required = false) Boolean wholeWord,
        @P(name = "scope", value = "The section of the flow YAML to search; default ALL", required = false) SourceSearchScope scope,
        @P(name = "page", value = "The 1-based page to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        int pageNumber = ToolPages.pageNumber(page);
        var results = sourceSearchService.search(
            PageableUtils.from(pageNumber, MAX_FLOWS),
            context.tenant(),
            namespace,
            query,
            Boolean.TRUE.equals(caseSensitive),
            Boolean.TRUE.equals(wholeWord),
            Boolean.TRUE.equals(regex),
            scope == null ? SourceSearchScope.ALL : scope
        );

        return new Result(
            results.stream().map(SearchFlowSourcesTool::toFlowMatches).toList(),
            results.getTotal(),
            pageNumber
        );
    }

    private static FlowMatches toFlowMatches(final SourceSearchResult result) {
        return new FlowMatches(
            result.namespace(),
            result.id(),
            result.matches().stream()
                .limit(MAX_MATCHES_PER_FLOW)
                .map(match -> new Match(match.line(), match.snippet()))
                .toList()
        );
    }

    public record Result(List<FlowMatches> flows, long total, int page) {
    }

    public record FlowMatches(String namespace, String id, List<Match> matches) {
    }

    /**
     * @param line the 1-based line number of the match
     * @param snippet the matched line, with the match wrapped in {@code [mark]}/{@code [/mark]}
     */
    public record Match(int line, String snippet) {
    }
}
