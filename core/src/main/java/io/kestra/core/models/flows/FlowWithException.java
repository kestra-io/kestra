package io.kestra.core.models.flows;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.exceptions.UnknownPropertyException;
import io.kestra.core.serializers.JacksonMapper;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class FlowWithException extends FlowWithSource {
    String exception;

    public static FlowWithException from(final FlowInterface flow, final Exception exception) {
        return FlowWithException.builder()
            .id(flow.getId())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .revision(flow.getRevision())
            .deleted(flow.isDeleted())
            .disabled(flow.isDisabled())
            .exception(exceptionMessage(flow.getNamespace(), flow.getId(), exception))
            .tasks(List.of())
            // an execution is still created for a blocked flow and then failed, so it must keep carrying these:
            // dropping them leaves label-based filtering, notifications and SLA alerting blind to the failure
            .labels(flow.getLabels())
            .variables(flow.getVariables())
            .source(flow.getSource())
            .build();
    }

    public static Optional<FlowWithException> from(final String source, final Exception exception, final Logger log) {
        log.error("Unable to deserialize a flow: {}", exception.getMessage());
        try {
            var jsonNode = JacksonMapper.ofJson().readTree(source);
            return FlowWithException.from(jsonNode, exception);
        } catch (IOException e) {
            // if we cannot create a FlowWithException, ignore the message
            log.error("Unexpected exception when trying to handle a deserialization error", e);
            return Optional.empty();
        }
    }

    public static Optional<FlowWithException> from(JsonNode jsonNode, Exception exception) {
        if (jsonNode.hasNonNull("id") && jsonNode.hasNonNull("namespace")) {
            final String id = jsonNode.get("id").asText();
            final String namespace = jsonNode.get("namespace").asText();

            final String tenantId;
            if (jsonNode.hasNonNull("tenant_id")) {
                // JsonNode is from database
                tenantId = jsonNode.get("tenant_id").asText();
            } else if (jsonNode.hasNonNull("tenantId")) {
                // JsonNode is from queue
                tenantId = jsonNode.get("tenantId").asText();
            } else {
                tenantId = null;
            }

            var flow = FlowWithException.builder()
                .id(id)
                .tenantId(tenantId)
                .namespace(namespace)
                .revision(jsonNode.hasNonNull("revision") ? jsonNode.get("revision").asInt() : 1)
                .deleted(jsonNode.hasNonNull("deleted") && jsonNode.get("deleted").asBoolean())
                .disabled(jsonNode.hasNonNull("disabled") && jsonNode.get("disabled").asBoolean())
                .exception(exceptionMessage(namespace, id, exception))
                .tasks(List.of())
                .source(jsonNode.hasNonNull("source") ? jsonNode.get("source").asText() : null)
                .build();
            return Optional.of(flow);
        }

        // if there is no id and namespace, we return null as we cannot create a meaningful FlowWithException
        return Optional.empty();
    }

    /**
     * The message shown for this flow by the API and the UI.
     * <p>
     * A model that frames its own error — see {@link io.kestra.core.models.triggers.AbstractTrigger} rejecting
     * a property it does not declare — has that message buried under Jackson's wrapping
     * ({@code Problem deserializing "any-property" … (through reference chain …)}). The framed message is what
     * tells the user what to change, so it is used instead, prefixed with the flow it belongs to since this
     * text also travels to logs and execution failures where the flow is not otherwise named.
     */
    private static String exceptionMessage(String namespace, String id, Exception exception) {
        // bounded: a cause chain this deep is a bug, and this must never loop on a self-referencing cause
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 10; depth++) {
            if (cause instanceof UnknownPropertyException unknownProperty) {
                return "Flow '" + namespace + "/" + id + "': " + unknownProperty.getMessage();
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }

        return exception.getMessage();
    }

    /** {@inheritDoc} **/
    @Override
    public Flow toFlow() {
        return this;
    }
}
