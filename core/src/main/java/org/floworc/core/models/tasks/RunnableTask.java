package org.floworc.core.models.tasks;

import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;

public interface RunnableTask {
    RunOutput run(RunContext runContext) throws Exception;
}
