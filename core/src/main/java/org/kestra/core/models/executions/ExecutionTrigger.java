package org.kestra.core.models.executions;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import javax.validation.constraints.NotNull;

@Value
@Builder
@Slf4j
public class ExecutionTrigger {
    @NotNull
    private String id;

    @NotNull
    private String type;

    private Map<String, Object> variables;
}
