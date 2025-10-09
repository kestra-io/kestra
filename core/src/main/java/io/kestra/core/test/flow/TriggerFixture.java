package io.kestra.core.test.flow;

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
public class TriggerFixture {
    @NotNull
    private String id;

    @NotNull
    private String type;

    private Map<String, Object> variables;
}
