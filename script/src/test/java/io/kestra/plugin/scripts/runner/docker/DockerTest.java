package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.TaskRunner;


class DockerTest extends AbstractTaskRunnerTest {
    @Override
    protected TaskRunner<?> taskRunner() {
        return Docker.builder().image("centos").build();
    }
}