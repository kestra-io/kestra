package io.kestra.core.test.flow;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fixtures {

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
    private Map<String, Object> inputs;

    private Map<String, String> files;

    @Valid
    private List<TaskFixture> tasks;

    @Valid
    private TriggerFixture trigger;
}
