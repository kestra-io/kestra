package io.kestra.core.models.topologies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.HasUID;
import lombok.Builder;
import lombok.Value;

import jakarta.validation.constraints.NotNull;

@Value
@Builder
public class FlowTopology implements HasUID {
    @NotNull
    FlowNode source;

    @NotNull
    FlowRelation relation;

    @NotNull
    FlowNode destination;


    /** {@inheritDoc **/
    @Override
    @JsonIgnore
    public String uid() {
        // we use destination as prefix to enable prefixScan on FlowTopologyUpdateTransformer
        return destination.getUid() + "|" + source.getUid();
    }
}
