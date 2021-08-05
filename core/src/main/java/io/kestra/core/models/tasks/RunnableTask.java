package io.kestra.core.models.tasks;

import io.kestra.core.runners.RunContext;

import java.io.IOException;

public interface RunnableTask <T extends Output> {
    T run(RunContext runContext) throws Exception;

    void cleanup() throws IOException;
}
