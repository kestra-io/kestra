package org.kestra.core.models.tasks;

import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;

public interface RunnableTask {
    RunOutput run(RunContext runContext) throws Exception;
}
