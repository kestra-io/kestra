package org.kestra.core.models.tasks;

import org.kestra.core.exceptions.InvalidFlowStateException;
import org.kestra.core.runners.RunContext;

import java.util.Map;

public interface RunContextVariableProvider {
    Map<String, Object> getVariables(RunContext runContext);
}
