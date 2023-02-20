package io.kestra.core.models.topologies;

import io.kestra.core.models.flows.Flow;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@Getter
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public class FlowNode {
    @NotNull
    String uid;

    String namespace;

    String id;

    public static FlowNode of(Flow flow) {
        return FlowNode.builder()
            .uid(flow.uidWithoutRevision())
            .namespace(flow.getNamespace())
            .id(flow.getId())
            .build();
    }
}
