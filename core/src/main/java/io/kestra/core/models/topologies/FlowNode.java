package io.kestra.core.models.topologies;

import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.FlowInterface;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FlowNode flowNode = (FlowNode) o;
        return Objects.equals(uid, flowNode.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }

    public static FlowNode of(FlowInterface flow) {
        return FlowNode.builder()
            .uid(flow.uidWithoutRevision())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .id(flow.getId())
            .build();
    }
}
