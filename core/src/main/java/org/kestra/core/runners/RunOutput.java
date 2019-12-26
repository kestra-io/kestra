package org.kestra.core.runners;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
@AllArgsConstructor
public class RunOutput {
    private Map<String, Object> outputs;
}
