package io.kestra.core.test.flow;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fixtures {
    private Map<String, Object> inputs;

    private Map<String, String> files;

    @Valid
    private List<TaskFixture> tasks;

    @Valid
    private TriggerFixture trigger;
}
