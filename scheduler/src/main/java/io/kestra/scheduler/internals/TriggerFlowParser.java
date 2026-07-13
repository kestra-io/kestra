package io.kestra.scheduler.internals;

import org.slf4j.Logger;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.services.FlowParsingService;

/**
 * Owns the scheduler's reaction to runtime flow-parsing outcomes.
 */
public final class TriggerFlowParser {

    private TriggerFlowParser() {
    }

    /**
     * Parses the given flow for trigger evaluation. A flow rejected by governance is skipped ({@code null});
     * any other parse failure degrades to the flow as stored so existing triggers keep evaluating.
     */
    public static FlowWithSource parseOrSkip(final FlowParsingService flowParsingService, final FlowWithSource flow, final Logger logger) {
        try {
            return flowParsingService.parseForRuntime(flow);
        } catch (FlowBlockedException e) {
            logger.warn(
                "Flow on tenant {}, namespace '{}', flow '{}' is blocked by governance, skipping its triggers: {}",
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getId(),
                e.getMessage()
            );
            return null;
        } catch (Exception e) {
            logger.warn(
                "Can't parse flow on tenant {}, namespace '{}', flow '{}' with errors '{}'",
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getId(),
                e.getMessage(),
                e
            );
            return flow;
        }
    }
}
