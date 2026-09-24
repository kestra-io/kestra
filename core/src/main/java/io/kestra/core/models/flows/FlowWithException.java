package io.kestra.core.models.flows;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.models.Label;
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
            .exception(exception.getMessage())
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
                .disabled(jsonNode.hasNonNull("disabled") && jsonNode.get("disabled").asBoolean())
                .exception(exception.getMessage())
                .tasks(List.of())
                .labels(extractLabels(jsonNode))
                .variables(extractVariables(jsonNode))
                .source(jsonNode.hasNonNull("source") ? jsonNode.get("source").asText() : null)
                .build();
            return Optional.of(flow);
        }

        // if there is no id and namespace, we return null as we cannot create a meaningful FlowWithException
        return Optional.empty();
    }

    private static List<Label> extractLabels(final JsonNode jsonNode) {
        try {
            if (!jsonNode.hasNonNull("labels")) {
                return null;
            }
            JsonNode labelsNode = jsonNode.get("labels");
            if (labelsNode.isArray()) {
                return JacksonMapper.ofJson().convertValue(labelsNode, new TypeReference<List<Label>>() {
                });
            }
            if (labelsNode.isObject()) {
                Map<String, Object> map = JacksonMapper.ofJson().convertValue(labelsNode, JacksonMapper.MAP_TYPE_REFERENCE);
                return map.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null
                        && !String.valueOf(entry.getValue()).isEmpty())
                    .map(entry -> new Label(entry.getKey(), String.valueOf(entry.getValue())))
                    .toList();
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    private static Map<String, Object> extractVariables(final JsonNode jsonNode) {
        try {
            if (!jsonNode.hasNonNull("variables")) {
                return null;
            }
            return JacksonMapper.ofJson().convertValue(jsonNode.get("variables"), JacksonMapper.MAP_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@inheritDoc} **/
    @Override
    public Flow toFlow() {
        return this;
    }
}
