package io.kestra.core.models.topologies;

import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.Flow;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Getter
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public class FlowNode implements TenantInterface {
    @NotNull
    String uid;

    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String tenantId;

    String namespace;

    String id;

    public static FlowNode of(Flow flow) {
        return FlowNode.builder()
            .uid(flow.uidWithoutRevision())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .id(flow.getId())
            .build();
    }
}
