package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;

import jakarta.validation.ConstraintViolationException;

/**
 * Saves flows for the flow tools, surfacing a constraint violation unwrapped so the model reads the validation errors.
 */
final class FlowWrites {
    private FlowWrites() {
    }

    static FlowWithSource create(final FlowService flowService, final GenericFlow flow) {
        try {
            return flowService.create(flow);
        } catch (FlowProcessingException | QueueException e) {
            throw unwrap(flow, e);
        }
    }

    static FlowWithSource update(final FlowService flowService, final GenericFlow flow, final FlowInterface previous) {
        try {
            return flowService.update(flow, previous);
        } catch (FlowProcessingException | QueueException e) {
            throw unwrap(flow, e);
        }
    }

    /**
     * @return false when the flow was already in the requested state, so nothing was saved
     */
    static boolean setDisabled(final FlowRepositoryInterface flowRepository, final FlowService flowService,
        final String tenant, final String namespace, final String flowId, final boolean disabled) {
        FlowWithSource flow = flowRepository.findByIdWithSource(tenant, namespace, flowId)
            .orElseThrow(() -> new IllegalArgumentException("Flow not found: '%s.%s'".formatted(namespace, flowId)));
        if (disabled == flow.isDisabled()) {
            return false;
        }

        GenericFlow updated = GenericFlow.fromYaml(tenant, FlowService.injectDisabled(flow.getSource(), disabled));
        update(flowService, updated, flow);
        return true;
    }

    private static RuntimeException unwrap(final GenericFlow flow, final Exception e) {
        if (e.getCause() instanceof ConstraintViolationException violation) {
            return violation;
        }
        return new IllegalStateException("Failed to save flow '%s.%s': %s".formatted(flow.getNamespace(), flow.getId(), e.getMessage()), e);
    }
}
