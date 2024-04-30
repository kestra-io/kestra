package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.TaskRunner;


class DockerTaskRunnerTest extends AbstractTaskRunnerTest {
    @Override
    protected TaskRunner taskRunner() {
        return DockerTaskRunner.builder().image("centos").build();
    }
}