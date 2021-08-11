package io.kestra.core.tasks;

import io.kestra.core.tasks.scripts.AbstractBash;
import io.kestra.core.tasks.scripts.Bash;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
class DockerBashTest extends AbstractBashTest {
    @Override
    protected Bash.BashBuilder<?, ?> configure(Bash.BashBuilder<?, ?> builder) {
        return builder
            .id(this.getClass().getSimpleName())
            .type(Bash.class.getName())
            .runner(AbstractBash.Runner.DOCKER)
            .dockerOptions(AbstractBash.DockerOptions.builder()
                .image("ubuntu")
                .build()
            );
    }
}
