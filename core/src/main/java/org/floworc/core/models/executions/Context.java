package org.floworc.core.models.executions;

import lombok.Value;
import org.floworc.core.models.flows.State;

import java.util.Map;

@Value
public class Context {
    private Map<String, Object> inputs;

    private Map<String, Object> outputs;

    private State state;
}
