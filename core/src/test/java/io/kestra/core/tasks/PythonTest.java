package io.kestra.core.tasks;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.tasks.scripts.AbstractBash;
import io.kestra.core.tasks.scripts.ScriptOutput;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tasks.scripts.Bash;
import io.kestra.core.tasks.scripts.Python;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class PythonTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "print('::{\"outputs\": {\"extract\":\"hello world\"}}::')");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("python3")
            .inputFiles(files)
            .build();

        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("hello world"));
    }

    @Test
    void failed() {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "import sys; sys.exit(1)");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("python3")
            .inputFiles(files)
            .build();

        Bash.BashException pythonException = assertThrows(Bash.BashException.class, () -> {
            python.run(runContext);
        });

        assertThat(pythonException.getExitCode(), is(1));
        assertThat(pythonException.getStdOutSize(), is(0));
    }

    @Test
    void requirements() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "import requests; print('::{\"outputs\": {\"extract\":\"' + str(requests.get('http://google.com').status_code) + '\"}}::')");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("python3")
            .inputFiles(files)
            .requirements(Collections.singletonList("requests"))
            .build();

        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("200"));
    }

    @Test
    void docker() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "import requests; print('::{\"outputs\": {\"extract\":\"' + str(requests.get('http://google.com').status_code) + '\"}}::')");

        Python python = Python.builder()
            .id("test-python-task")
            .type(Python.class.getName())
            .inputFiles(files)
            .runner(AbstractBash.Runner.DOCKER)
            .dockerOptions(AbstractBash.DockerOptions.builder()
                .image("python")
                .build()
            )
            .requirements(Collections.singletonList("requests"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of());

        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("200"));
    }

    @Test
    void manyFiles() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "import otherfile; otherfile.test()");
        files.put("otherfile.py", "def test(): print('::{\"outputs\": {\"extract\":\"success\"}}::')");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("python3")
            .inputFiles(files)
            .build();

        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("success"));
    }

    @Test
    void pipConf() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "print('::{\"outputs\": {\"extract\":\"' + str('#it worked !' in open('pip.conf').read()) + '\"}}::')");
        files.put("pip.conf", "[global]\nno-cache-dir = false\n#it worked !");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("python3")
            .inputFiles(files)
            .build();

        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("True"));
    }

    @Test
    void fileInSubFolders() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "print('::{\"outputs\": {\"extract\":\"' + open('sub/folder/file/test.txt').read() + '\"}}::')");
        files.put("sub/folder/file/test.txt", "OK");
        files.put("sub/folder/file/test1.txt", "OK");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("python3")
            .inputFiles(files)
            .build();

        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("OK"));
    }

    @Test
    void args() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of("test", "value"));
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "import sys; print('::{\"outputs\": {\"extract\":\"' + ' '.join(sys.argv) + '\"}}::')");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("python3")
            .inputFiles(files)
            .args(Arrays.asList("test", "param", "{{test}}"))
            .build();

        ScriptOutput run = python.run(runContext);

        assertThat(run.getVars().get("extract"), is("main.py test param value"));
    }

    @Test
    void outputs() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of("test", "value"));
        Map<String, String> files = new HashMap<>();
        files.put("main.py", "from kestra import Kestra\n" +
            "import time\n" +
            "Kestra.outputs({'test': 'value', 'int': 2, 'bool': True, 'float': 3.65})\n" +
            "Kestra.counter('count', 1, {'tag1': 'i', 'tag2': 'win'})\n" +
            "Kestra.counter('count2', 2)\n" +
            "Kestra.timer('timer1', lambda: time.sleep(1), {'tag1': 'i', 'tag2': 'lost'})\n" +
            "Kestra.timer('timer2', 2.12, {'tag1': 'i', 'tag2': 'destroy'})\n"
        );

        Python node = Python.builder()
            .id("test-node-task")
            .pythonPath("python3")
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getVars().get("test"), is("value"));
        assertThat(run.getVars().get("int"), is(2));
        assertThat(run.getVars().get("bool"), is(true));
        assertThat(run.getVars().get("float"), is(3.65));

        AbstractBashTest.controlOutputs(runContext, run);
    }
}
