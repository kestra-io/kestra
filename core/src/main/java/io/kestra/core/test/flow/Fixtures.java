package io.kestra.core.test.flow;

import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class Fixtures {
    private Map<String, Object> inputs;

    private Map<String, String> files;

    @Valid
    private List<TaskFixture> tasks;

    @Valid
    private TriggerFixture trigger;
}
