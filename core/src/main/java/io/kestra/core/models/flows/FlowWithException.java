package io.kestra.core.models.flows;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.core.annotation.Introspected;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
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
            .exception(exception.getMessage())
            .tasks(List.of())
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
                .id(jsonNode.get("id").asText())
                .tenantId(tenantId)
                .namespace(jsonNode.get("namespace").asText())
                .revision(jsonNode.hasNonNull("revision") ? jsonNode.get("revision").asInt() : 1)
                .deleted(jsonNode.hasNonNull("deleted") && jsonNode.get("deleted").asBoolean())
                .exception(exception.getMessage())
                .tasks(List.of())
                .source(jsonNode.hasNonNull("source") ? jsonNode.get("source").asText() : null)
                .build();
            return Optional.of(flow);
        }

        // if there is no id and namespace, we return null as we cannot create a meaningful FlowWithException
        return Optional.empty();
    }

    /** {@inheritDoc} **/
    @Override
    public Flow toFlow() {
        return this;
    }
}
