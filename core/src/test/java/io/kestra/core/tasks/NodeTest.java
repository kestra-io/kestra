package io.kestra.core.tasks;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.tasks.scripts.ScriptOutput;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tasks.scripts.Bash;
import io.kestra.core.tasks.scripts.Node;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

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
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"hello world\"}}::')");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("hello world"));
        assertThat(run.getStdErrLineCount(), equalTo(0));
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
        assertThat(nodeException.getStdOutSize(), is(0));
        assertThat(nodeException.getStdErrSize(), equalTo(0));
    }

    @Test
    void requirements() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "require('axios').get('http://google.com').then(r => { console.log('::{\"outputs\": {\"extract\":\"' + r.status + '\"}}::') })");
        files.put("package.json", "{\"dependencies\":{\"axios\":\"^0.20.0\"}}");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .npmPath("npm")
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("200"));
    }

    @Test
    void manyFiles() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (require('./otherfile').value) + '\"}}::')");
        files.put("otherfile.js", "module.exports.value = 'success'");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("success"));
    }

    @Test
    void fileInSubFolders() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (require('fs').readFileSync('./sub/folder/file/test.txt', 'utf-8')) + '\"}}::')");
        files.put("sub/folder/file/test.txt", "OK");
        files.put("sub/folder/file/test1.txt", "OK");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("OK"));
    }

    @Test
    void args() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of("test", "value"));
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (process.argv.slice(2).join(' ')) + '\"}}::')");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .args(Arrays.asList("test", "param", "{{test}}"))
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getVars().get("extract"), is("test param value"));
    }

    @Test
    void outputs() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of("test", "value"));
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "const Kestra = require(\"./kestra\");" +
            "Kestra.outputs({test: 'value', int: 2, bool: true, float: 3.65});" +
            "Kestra.counter('count', 1, {tag1: 'i', tag2: 'win'});" +
            "Kestra.counter('count2', 2);" +
            "Kestra.timer('timer1', (callback) => { setTimeout(callback, 1000) }, {tag1: 'i', tag2: 'lost'});" +
            "Kestra.timer('timer2', 2.12, {tag1: 'i', tag2: 'destroy'});"
        );

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath("node")
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        BashTest.controlOutputs(runContext, run);
    }
}
