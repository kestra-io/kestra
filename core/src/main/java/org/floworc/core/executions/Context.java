package org.floworc.core.executions;

import lombok.Data;
import org.floworc.core.flows.State;

import java.util.Map;

@Data
public class Context {
    private Map<String, Object> inputs;

    private Map<String, Object> outputs;

    private State state;
}
