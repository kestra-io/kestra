package org.kestra.core.tasks;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.runners.RunContext;
import org.kestra.core.storages.StorageInterface;
import org.kestra.core.tasks.scripts.Bash;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@MicronautTest
class BashTest {
    @Inject
    ApplicationContext applicationContext;

    @Test
    void run() throws Exception {
        RunContext runContext = new RunContext(
            this.applicationContext,
            ImmutableMap.of(
                "input", ImmutableMap.of("url", "www.google.fr")
            )
        );

        Bash bash = Bash.builder()
            .commands(new String[]{"sleep 1", "curl {{ upper input.url }} > /dev/null", "echo 0", "sleep 1", "echo 1"})
            .build();

        bash.run(runContext);
    }
}