package org.kestra.core.models.tasks;

import org.kestra.core.runners.RunContext;

public interface RunnableTask <T extends Output> {
    T run(RunContext runContext) throws Exception;
}
