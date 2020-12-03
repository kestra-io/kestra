package org.kestra.core.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.kestra.core.models.executions.AbstractMetricEntry;
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
import java.time.Duration;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
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
        assertThat(run.getStdOutLineCount(), is(2));
        assertThat(run.getStdErrLineCount() > 0, is(true));
    }

    @Test
    void files() throws Exception {
        RunContext runContext = runContextFactory.of();

        Bash bash = Bash.builder()
            .outputsFiles(Arrays.asList("xml", "csv"))
            .inputFiles(ImmutableMap.of("files/in/in.txt", "I'm here"))
            .commands(new String[]{
                "echo '::{\"outputs\": {\"extract\":\"'$(cat files/in/in.txt)'\"}}::'",
                "echo 1 >> {{ outputFiles.xml }}",
                "echo 2 >> {{ outputFiles.csv }}",
                "echo 3 >> {{ outputFiles.xml }}"
            })
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdErrLineCount(), is(0));

        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("I'm here"));

        InputStream get = storageInterface.get(run.getFiles().get("xml"));

        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is("1\n3\n")
        );

        get = storageInterface.get(run.getOutputFiles().get("csv"));

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
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(1));
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
        assertThat(run.getStdOutLineCount(), is(2));
        assertThat(run.getStdErrLineCount() > 0, is(false));
    }

    @Test
    void useInputFiles() throws Exception {
        RunContext runContext = runContextFactory.of();

        Map<String, String> files = new HashMap<>();
        files.put("test.sh", "tst() { echo '::{\"outputs\": {\"extract\":\"testbash\"}}::' ; echo '{{workingDir}}'; }");

        List<String> commands = new ArrayList<>();
        commands.add("source {{workingDir}}/test.sh && tst");

        Bash bash = Bash.builder()
            .interpreter("/bin/bash")
            .commands(commands.toArray(String[]::new))
            .inputFiles(files)
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("testbash"));
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
        commands.add("cat fscontent.txt > {{ outputFiles.out }} ");

        Bash bash = Bash.builder()
            .interpreter("/bin/bash")
            .commands(commands.toArray(String[]::new))
            .inputFiles(files)
            .outputsFiles(Collections.singletonList("out"))
            .build();

        Bash.Output run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        InputStream get = storageInterface.get(run.getOutputFiles().get("out"));
        String outputContent = CharStreams.toString(new InputStreamReader(get));
        String fileContent = String.join("\n", Files.readAllLines(new File(resource.getPath()).toPath(), StandardCharsets.UTF_8));
        assertThat(outputContent, is(fileContent));
    }

    static void controlOutputs(RunContext runContext, Bash.Output run) {
        assertThat(run.getVars().get("test"), is("value"));
        assertThat(run.getVars().get("int"), is(2));
        assertThat(run.getVars().get("bool"), is(true));
        assertThat(run.getVars().get("float"), is(3.65));

        assertThat(BashTest.getMetrics(runContext, "count").getValue(), is(1D));
        assertThat(BashTest.getMetrics(runContext, "count").getTags().size(), is(2));
        assertThat(BashTest.getMetrics(runContext, "count").getTags().get("tag1"), is("i"));
        assertThat(BashTest.getMetrics(runContext, "count").getTags().get("tag2"), is("win"));

        assertThat(BashTest.<Duration>getMetrics(runContext, "timer1").getValue().getNano(), greaterThan(0));
        assertThat(BashTest.<Duration>getMetrics(runContext, "timer1").getTags().size(), is(2));
        assertThat(BashTest.<Duration>getMetrics(runContext, "timer1").getTags().get("tag1"), is("i"));
        assertThat(BashTest.<Duration>getMetrics(runContext, "timer1").getTags().get("tag2"), is("lost"));

        assertThat(BashTest.<Duration>getMetrics(runContext, "timer2").getValue().getNano(), greaterThan(100000000));
        assertThat(BashTest.<Duration>getMetrics(runContext, "timer2").getTags().size(), is(2));
        assertThat(BashTest.<Duration>getMetrics(runContext, "timer2").getTags().get("tag1"), is("i"));
        assertThat(BashTest.<Duration>getMetrics(runContext, "timer2").getTags().get("tag2"), is("destroy"));
    }

    @SuppressWarnings("unchecked")
    static <T> AbstractMetricEntry<T> getMetrics(RunContext runContext, String name) {
        return (AbstractMetricEntry<T>) runContext.metrics()
            .stream()
            .filter(abstractMetricEntry -> abstractMetricEntry.getName().equals(name))
            .findFirst()
            .orElseThrow();

    }
}
