package org.kestra.core.tasks;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.kestra.core.runners.RunContext;
import org.kestra.core.tasks.scripts.Bash;

import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().size(), is(2));
        assertThat(run.getStdErr().size() > 0, is(true));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
    void failed() {
        RunContext runContext = new RunContext(this.applicationContext, ImmutableMap.of());

        Bash bash = Bash.builder()
            .commands(new String[]{"echo 1 1>&2", "exit 66", "echo 2"})
            .build();

        Bash.BashException bashException = assertThrows(Bash.BashException.class, () -> {
            bash.run(runContext);
        });


        assertThat(bashException.getExitCode(), is(66));
        assertThat(bashException.getStdOut().size(), is(0));
        assertThat(bashException.getStdErr().size(), is(1));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
    void stopOnFirstFailed() {
        RunContext runContext = new RunContext(this.applicationContext, ImmutableMap.of());

        Bash bash = Bash.builder()
            .commands(new String[]{"unknown", "echo 1"})
            .build();

        Bash.BashException bashException = assertThrows(Bash.BashException.class, () -> {
            bash.run(runContext);
        });

        assertThat(bashException.getExitCode(), is(127));
        assertThat(bashException.getStdOut().size(), is(0));
        assertThat(bashException.getStdErr().size(), is(1));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
    void dontStopOnFirstFailed() throws Exception {
        RunContext runContext = new RunContext(this.applicationContext, ImmutableMap.of());

        Bash bash = Bash.builder()
            .commands(new String[]{"unknown", "echo 1"})
            .exitOnFailed(false)
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().size(), is(1));
        assertThat(run.getStdErr().size(), is(1));
    }
}
