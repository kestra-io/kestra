package io.kestra.core.test.flow;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class TaskFixture {
    @NotNull
    private String id;

    private String value;

    @Builder.Default
    private State.Type state = State.Type.SUCCESS;

    private Map<String, Object> outputs;

    private Property<String> description;
}
