package io.kestra.core.test.flow;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TriggerFixture {
    @NotNull
    private String id;

    @NotNull
    private String type;

    private Map<String, Object> variables;
}
