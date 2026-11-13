package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.Schedulable;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.scheduler.events.CreateBackfillTrigger;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.webserver.services.TriggerStateService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class BackfillFlowTool implements McpPlatformTool {
    private final FlowRepositoryInterface flowRepository;
    private final TriggerStateService triggerStateService;

    @Inject
    public BackfillFlowTool(final FlowRepositoryInterface flowRepository, final TriggerStateService triggerStateService) {
        this.flowRepository = flowRepository;
        this.triggerStateService = triggerStateService;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.ACT;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.CONFIRM;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.DESTRUCTIVE;
    }

    @Tool(
        name = "backfill_flow", value = "Backfill a Kestra flow's schedule trigger over a past time window: the scheduler creates one execution for every date the schedule would have fired between start and end. "
            + "A flow with a single schedule trigger needs no triggerId; with several, pass the one to backfill. "
            + "Blocks until the scheduler accepts the backfill, then returns { namespace, flowId, triggerId, start, end, currentDate, paused }; "
            + "the executions are then created asynchronously, so use list_executions to follow them."
    )
    public Result backfillFlow(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        @P(name = "start", value = "Start of the window: an ISO-8601 date-time with offset (e.g. 2026-01-01T00:00:00Z), or an ISO-8601 duration counted back from now (e.g. P2D, PT6H)") String start,
        @P(name = "end", value = "Optional end of the window, as an ISO-8601 date-time with offset; omit to backfill up to now", required = false) String end,
        @P(name = "triggerId", value = "Optional id of the schedule trigger to backfill; required only when the flow has several", required = false) String triggerId,
        @P(name = "inputs", value = "Optional inputs for the backfilled executions, keyed by input id", required = false) Map<String, Object> inputs,
        @P(name = "labels", value = "Optional labels to add to the backfilled executions, as key/value pairs", required = false) Map<String, String> labels,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime startDate = parseStart(start, now);
        ZonedDateTime endDate = end == null ? now : parseDateTime("end", end);

        TriggerId trigger = TriggerId.of(tenant, namespace, flowId, triggerId != null ? triggerId : scheduleTriggerOf(tenant, namespace, flowId));
        CreateBackfillTrigger.Backfill backfill = new CreateBackfillTrigger.Backfill(
            startDate, endDate, MapUtils.emptyOnNull(inputs),
            MapUtils.emptyOnNull(labels).entrySet().stream().map(label -> new Label(label.getKey(), label.getValue())).toList()
        );

        Backfill created;
        try {
            created = triggerStateService.createBackfill(trigger, backfill).getBackfill();
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Trigger '%s' of flow '%s.%s' not found.".formatted(trigger.getTriggerId(), namespace, flowId), e);
        }

        return new Result(
            namespace, flowId, trigger.getTriggerId(),
            created.getStart().toString(),
            Optional.ofNullable(created.getEnd()).map(ZonedDateTime::toString).orElse(null),
            Optional.ofNullable(created.getCurrentDate()).map(ZonedDateTime::toString).orElse(null),
            Boolean.TRUE.equals(created.getPaused())
        );
    }

    private String scheduleTriggerOf(final String tenant, final String namespace, final String flowId) {
        Flow flow = flowRepository.findByIdWithoutAcl(tenant, namespace, flowId, Optional.empty())
            .orElseThrow(() -> new IllegalArgumentException("Flow '%s.%s' not found.".formatted(namespace, flowId)));
        List<String> scheduleTriggers = ListUtils.emptyOnNull(flow.getTriggers()).stream()
            .filter(Schedulable.class::isInstance)
            .map(AbstractTrigger::getId)
            .toList();
        if (scheduleTriggers.size() != 1) {
            throw new IllegalArgumentException(
                "Flow '%s.%s' has %d schedule triggers %s: pass the triggerId to backfill."
                    .formatted(namespace, flowId, scheduleTriggers.size(), scheduleTriggers)
            );
        }
        return scheduleTriggers.getFirst();
    }

    private static ZonedDateTime parseStart(final String start, final ZonedDateTime now) {
        if (start.startsWith("P")) {
            try {
                return now.minus(Duration.parse(start));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid start duration '%s': expected an ISO-8601 duration such as P2D or PT6H.".formatted(start), e);
            }
        }
        return parseDateTime("start", start);
    }

    private static ZonedDateTime parseDateTime(final String name, final String value) {
        try {
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid %s '%s': expected an ISO-8601 date-time with offset such as 2026-01-01T00:00:00Z.".formatted(name, value), e);
        }
    }

    /**
     * The backfill as recorded on the trigger, with ISO-8601 dates.
     *
     * @param currentDate the date the backfill has reached, null until the scheduler starts it
     */
    public record Result(String namespace, String flowId, String triggerId, String start, String end, String currentDate, boolean paused) {
    }
}
