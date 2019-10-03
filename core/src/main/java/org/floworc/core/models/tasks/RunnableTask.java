package org.floworc.core.models.tasks;

import org.floworc.core.runners.RunContext;

public interface RunnableTask {
    Void run(RunContext runContext) throws Exception;
}
