package org.kestra.webserver.responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kestra.core.models.flows.Flow;

import javax.validation.ConstraintViolation;
import javax.validation.constraints.NotNull;
import org.apache.avro.reflect.Nullable;
import java.util.Set;

@Getter
@NoArgsConstructor
public class FlowResponse {
    @NotNull
    private Flow flow;

    @Nullable
    private Set<ConstraintViolation<Flow>> errors;

    private FlowResponse(Flow flow, Set<ConstraintViolation<Flow>> errors) {
        this.flow = flow;
        this.errors = errors;
    }

    public FlowResponse(Flow flow) {
        this.flow = flow;
    }

    public static FlowResponse of (Flow flow, Set<ConstraintViolation<Flow>> errors) {
        return new FlowResponse(flow, errors);
    }

    public static FlowResponse of (Flow flow) {
        return new FlowResponse(flow);
    }
}