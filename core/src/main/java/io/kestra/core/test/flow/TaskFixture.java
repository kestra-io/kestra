package io.kestra.core.test.flow;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TaskFixture {
    @NotNull
    private String id;

    private String value;

    @Builder.Default
    private State.Type state = State.Type.SUCCESS;

    private Map<String, Object> outputs;

    private Property<String> description;
}
