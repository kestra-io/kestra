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
import org.kestra.core.tasks.scripts.Python;

import javax.inject.Inject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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
        HashMap<String, String> files = new HashMap<String, String>();
        files.put("main.py","print('hello world')");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("/usr/bin/python3")
            .inputFiles(files)
            .build();

        Bash.Output run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().size(), is(1));
        assertThat(run.getStdOut().get(0), is("hello world"));
        assertThat(run.getStdErr().size(), equalTo(0));
    }

    @Test
    void failed() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<String, String>();
        files.put("main.py","import sys; sys.exit(1)");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("/usr/bin/python3")
            .inputFiles(files)
            .build();

       Bash.BashException pythonException = assertThrows(Bash.BashException.class, () -> {
            python.run(runContext);
        });

        assertThat(pythonException.getExitCode(), is(1));
        assertThat(pythonException.getStdOut().size(), is(0));
        assertThat(pythonException.getStdErr().size(), equalTo(0));
    }

    @Test
    void requirements() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<String, String>();
        files.put("main.py","import requests; print(requests.get('http://google.com').status_code)");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("/usr/bin/python3")
            .inputFiles(files)
            .requirements(new String[]{"requests"})
            .build();

        Bash.Output run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().get(0), is("200"));
    }

    @Test
    void manyFiles() throws Exception {
        RunContext runContext = runContextFactory.of();
        HashMap<String, String> files = new HashMap<String, String>();
        files.put("main.py","import otherfile; otherfile.test()");
        files.put("otherfile.py","def test(): print('success')");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("/usr/bin/python3")
            .inputFiles(files)
            .build();

        Bash.Output run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().get(0), is("success"));
    }

    @Test
    void pipConf() throws Exception {
        RunContext runContext = runContextFactory.of();
        HashMap<String, String> files = new HashMap<String, String>();
        files.put("main.py","print(open('pip.conf').read())");
        files.put("pip.conf","[global]\nno-cache-dir = false\n#it worked !");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("/usr/bin/python3")
            .inputFiles(files)
            .build();

        Bash.Output run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().get(2), is("#it worked !"));
    }

    @Test
    void fileInSubFolders() throws Exception {
        RunContext runContext = runContextFactory.of();
        HashMap<String, String> files = new HashMap<String, String>();
        files.put("main.py","print(open('sub/folder/file/test.txt').read())");
        files.put("sub/folder/file/test.txt","OK");
        files.put("sub/folder/file/test1.txt","OK");

        Python python = Python.builder()
            .id("test-python-task")
            .pythonPath("/usr/bin/python3")
            .inputFiles(files)
            .build();

        Bash.Output run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().get(0), is("OK"));
    }

}
