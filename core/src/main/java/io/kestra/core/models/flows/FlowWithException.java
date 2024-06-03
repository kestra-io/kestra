package io.kestra.core.models.flows;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.models.executions.Execution;
import io.micronaut.core.annotation.Introspected;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

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

    public static Optional<FlowWithException> from(JsonNode jsonNode, Exception exception) {
        if (jsonNode.hasNonNull("id") && jsonNode.hasNonNull("namespace")) {
            var flow = FlowWithException.builder()
                .id(jsonNode.get("id").asText())
                .tenantId(jsonNode.hasNonNull("tenant_id") ? jsonNode.get("tenant_id").asText() : null)
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
}
