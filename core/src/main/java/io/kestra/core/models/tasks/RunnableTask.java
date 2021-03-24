package io.kestra.core.models.tasks;

import io.kestra.core.runners.RunContext;

public interface RunnableTask <T extends Output> {
    T run(RunContext runContext) throws Exception;
}
