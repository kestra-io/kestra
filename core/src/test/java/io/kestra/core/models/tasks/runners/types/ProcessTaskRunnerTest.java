package io.kestra.core.models.tasks.runners.types;

import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.tasks.runners.types.ProcessTaskRunner;

class ProcessTaskRunnerTest extends AbstractTaskRunnerTest {

    @Override
    protected TaskRunner taskRunner() {
        return new ProcessTaskRunner();
    }
}