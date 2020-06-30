package org.kestra.core.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.kestra.core.runners.RunContext;
import org.kestra.core.storages.StorageInterface;
import org.kestra.core.tasks.scripts.Bash;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class BashTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    StorageInterface storageInterface;

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
    void files() throws Exception {
        RunContext runContext = new RunContext(this.applicationContext, ImmutableMap.of());

        Bash bash = Bash.builder()
            .files(Arrays.asList("xml", "csv"))
            .commands(new String[]{"echo 1 >> {{ temp.xml }}", "echo 2 >> {{ temp.csv }}", "echo 3 >> {{ temp.xml }}"})
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdErr().size(), is(0));

        InputStream get = storageInterface.get(run.getFiles().get("xml"));

        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is("1\n3\n")
        );

        get = storageInterface.get(run.getFiles().get("csv"));

        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is("2\n")
        );
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

    @Test
    void longBashCreateTempFiles() throws Exception {
        RunContext runContext = new RunContext(this.applicationContext, ImmutableMap.of());

        List<String> commands = new ArrayList<>();
        for (int i = 0; i < 15000; i++) {
            commands.add("if [ \"" + i + "\" -eq 0 ] || [ \"" + i + "\" -eq 14999  ]; then echo " + i + ";fi;");
        }

        Bash bash = Bash.builder()
            .commands(commands.toArray(String[]::new))
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().size(), is(2));
        assertThat(run.getStdErr().size() > 0, is(false));
    }
}
