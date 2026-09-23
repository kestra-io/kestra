package io.kestra.scheduler.internals;

import org.slf4j.Logger;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.services.FlowParsingService;

/**
 * Owns the scheduler's reaction to runtime flow-parsing outcomes.
 */
public final class TriggerFlowParser {

    private TriggerFlowParser() {
    }

    /**
     * Parses the given flow for trigger evaluation, degrading to the flow as stored when parsing fails so
     * existing triggers keep evaluating.
     *
     * @return the parsed flow, or the stored flow when parsing failed
     *
     * @throws FlowProcessingException when parsing failed and the stored flow carries no trigger definitions (a
     *         deserialization failure kept as a {@code FlowWithException}): there is nothing to degrade to, and the
     *         message tells the caller why so it can report it against the trigger
     * @throws FlowBlockedException when governance rejects the flow, whose triggers must then not run. Checked
     *         deliberately: a caller has to decide what to report, and a block reported as an ordinary parse
     *         failure is the confusion this guards against.
     */
    public static FlowWithSource parseForTrigger(final FlowParsingService flowParsingService, final FlowWithSource flow, final Logger logger)
        throws FlowBlockedException, FlowProcessingException {
        try {
            return flowParsingService.parseForRuntime(flow).flow();
        } catch (FlowBlockedException e) {
            throw e;
        } catch (Exception e) {
            logger.warn(
                "Can't parse flow on tenant {}, namespace '{}', flow '{}' with errors '{}'",
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getId(),
                e.getMessage()
            );
            if (flow.getTriggers() == null) {
                throw e instanceof FlowProcessingException failure ? failure : new FlowProcessingException(e.getMessage());
            }
            return flow;
        }
    }
}
