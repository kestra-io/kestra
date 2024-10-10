package io.kestra.core.models.flows;

import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.FlowValidation;
import io.micronaut.core.annotation.Introspected;
import jakarta.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
@FlowValidation
public class FlowWithPath {
    private FlowWithSource flow;
    @Nullable
    private String tenantId;
    private String id;
    private String namespace;
    private String path;

    public static FlowWithPath of(FlowWithSource flow, String path) {
        return FlowWithPath.builder()
            .id(flow.getId())
            .namespace(flow.getNamespace())
            .path(path)
            .build();
    }

    public static FlowWithPath of(Flow flow, String path) {
        return FlowWithPath.builder()
            .id(flow.getId())
            .namespace(flow.getNamespace())
            .path(path)
            .build();
    }

    public String uidWithoutRevision() {
        return IdUtils.fromParts(
            tenantId,
            namespace,
            id
        );
    }
}
