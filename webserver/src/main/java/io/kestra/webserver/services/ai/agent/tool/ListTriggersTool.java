package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool listing flow triggers with their runtime state, matching per-field query filters.
 */
@Singleton
public class ListTriggersTool implements McpPlatformTool {
    private static final int MAX_RESULTS = 50;

    private final TriggerRepositoryInterface triggerRepository;

    @Inject
    public ListTriggersTool(final TriggerRepositoryInterface triggerRepository) {
        this.triggerRepository = triggerRepository;
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
        name = "list_flow_triggers",
        value = "List the triggers of Kestra flows with their runtime state, matching the given per-field filters (" + MAX_RESULTS + " per page). Read-only; use this to find which flows are scheduled, "
            + "when a trigger next runs, or which triggers are disabled or locked. Read a trigger's definition with read_flow. "
            + "Returns an object { triggers, total, page } where `triggers` is an array of { namespace, flowId, triggerId, type, disabled, disabledInSource, locked, nextEvaluationDate, lastTriggeredDate }. "
            + "`disabled` is true when the trigger does not run, either because its definition sets `disabled: true` (then `disabledInSource` is also true) or because it was disabled from the UI or the API. "
            + "`type` is SCHEDULE, POLLING or REALTIME; dates are ISO-8601 date-times or null. `total` is the number of all matching triggers; when it exceeds what was read so far, request the next `page`."
    )
    public Result listTriggers(
        @QueryFilterFormat(QueryFilter.Resource.TRIGGER) List<QueryFilter> filters,
        @P(name = "page", value = "The 1-based page to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        int pageNumber = ToolPages.pageNumber(page);
        var triggers = triggerRepository.find(PageableUtils.from(pageNumber, MAX_RESULTS), context.tenant(), filters);

        return new Result(
            triggers.stream().map(ListTriggersTool::toSummary).toList(),
            triggers.getTotal(),
            pageNumber
        );
    }

    private static TriggerSummary toSummary(final TriggerState state) {
        return new TriggerSummary(
            state.getNamespace(),
            state.getFlowId(),
            state.getTriggerId(),
            state.getType(),
            state.isDisabled() || state.isSourceDisabled(),
            state.isSourceDisabled(),
            state.isLocked(),
            state.getNextEvaluationDate(),
            state.getLastTriggeredDate()
        );
    }

    public record Result(List<TriggerSummary> triggers, long total, int page) {
    }

    public record TriggerSummary(
        String namespace,
        String flowId,
        String triggerId,
        TriggerType type,
        boolean disabled,
        boolean disabledInSource,
        boolean locked,
        Instant nextEvaluationDate,
        Instant lastTriggeredDate) {
    }
}
