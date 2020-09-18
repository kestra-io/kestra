package org.kestra.core.tasks;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.storages.StorageInterface;
import org.kestra.core.tasks.scripts.Bash;
import org.kestra.core.tasks.scripts.Node;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class NodeTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('hello world')");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .build();

        Bash.Output run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().size(), is(1));
        assertThat(run.getStdOut().get(0), is("hello world"));
        assertThat(run.getStdErr().size(), equalTo(0));
    }

    @Test
    void failed() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "process.exit(1)");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .build();

        Bash.BashException nodeException = assertThrows(Bash.BashException.class, () -> {
            node.run(runContext);
        });

        assertThat(nodeException.getExitCode(), is(1));
        assertThat(nodeException.getStdOut().size(), is(0));
        assertThat(nodeException.getStdErr().size(), equalTo(0));
    }

    @Test
    void requirements() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "require('axios').get('http://google.com').then(r => { console.log( r.status ) })");
        files.put("package.json", "{\"dependencies\":{\"axios\":\"^0.20.0\"}}");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .npmPath("npm")
            .inputFiles(files)
            .build();

        Bash.Output run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().get(0), is("200"));
    }

    @Test
    void manyFiles() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log(require('./otherfile').value)");
        files.put("otherfile.js", "module.exports.value = 'success'");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .build();

        Bash.Output run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().get(0), is("success"));
    }

    @Test
    void fileInSubFolders() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log(require('fs').readFileSync('./sub/folder/file/test.txt', 'utf-8'))");
        files.put("sub/folder/file/test.txt", "OK");
        files.put("sub/folder/file/test1.txt", "OK");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .build();

        Bash.Output run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOut().get(0), is("OK"));
    }

    @Test
    void args() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of("test", "value"));
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log(process.argv.slice(2).join(' '))");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .args(Arrays.asList("test", "param", "{{test}}"))
            .build();

        Bash.Output run = node.run(runContext);

        assertThat(run.getStdOut().get(0), is("test param value"));
    }
}
