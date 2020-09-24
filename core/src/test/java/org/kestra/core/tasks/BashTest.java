package org.kestra.core.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.storages.StorageInterface;
import org.kestra.core.tasks.scripts.Bash;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class BashTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of(
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
        RunContext runContext = runContextFactory.of();

        Bash bash = Bash.builder()
            .files(Arrays.asList("xml", "csv"))
            .inputFiles(ImmutableMap.of("files/in/in.txt", "I'm here"))
            .commands(new String[]{"cat files/in/in.txt", "echo 1 >> {{ outputFiles.xml }}", "echo 2 >> {{ outputFiles.csv }}", "echo 3 >> {{ outputFiles.xml }}"})
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdErr().size(), is(0));

        assertThat(run.getStdOut().size(), is(1));
        assertThat(run.getStdOut().get(0), is("I'm here"));

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
        RunContext runContext = runContextFactory.of();

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
        RunContext runContext = runContextFactory.of();

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
        RunContext runContext = runContextFactory.of();

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
        RunContext runContext = runContextFactory.of();

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

    @Test
    void useInputFiles() throws Exception {
        RunContext runContext = runContextFactory.of();

        Map<String, String> files = new HashMap<>();
        files.put("test.sh", "tst() { echo 'testbash' ; echo '{{workingDir}}'; }");

        List<String> commands = new ArrayList<>();
        commands.add("source {{workingDir}}/test.sh && tst");

        Bash bash = Bash.builder()
            .interpreter("/bin/bash")
            .commands(commands.toArray(String[]::new))
            .inputFiles(files)
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().get(0), is("testbash"));
    }

    @Test
    void useInputFilesFromKestraFs() throws Exception {
        RunContext runContext = runContextFactory.of();

        URL resource = BashTest.class.getClassLoader().getResource("application.yml");

        URI put = storageInterface.put(
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        Map<String, String> files = new HashMap<>();
        files.put("test.sh", "cat fscontent.txt");
        files.put("fscontent.txt", put.toString());

        List<String> commands = new ArrayList<>();
        commands.add("cat fscontent.txt");

        Bash bash = Bash.builder()
            .interpreter("/bin/bash")
            .commands(commands.toArray(String[]::new))
            .inputFiles(files)
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        String outputContent = String.join("\n", run.getStdOut());
        String fileContent = String.join("\n", Files.readAllLines(new File(resource.getPath()).toPath(), StandardCharsets.UTF_8));
        assertThat(outputContent, is(fileContent));
    }
}
