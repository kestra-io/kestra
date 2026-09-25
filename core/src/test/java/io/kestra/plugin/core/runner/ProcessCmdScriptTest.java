package io.kestra.plugin.core.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessCmdScriptTest {
    @TempDir
    Path workingDirectory;

    @Test
    void shouldRewriteMultilineCmdInvocationToBatchFile() throws Exception {
        List<String> commands = List.of("cmd.exe", "/c", "echo one\r\necho two");

        Optional<Path> script = Process.writeWindowsCmdScript(commands, workingDirectory);

        assertThat(script).isPresent();
        Path path = script.get();
        assertThat(path.getParent()).isEqualTo(workingDirectory);
        assertThat(path.getFileName().toString()).endsWith(".bat");
        assertThat(Files.readString(path)).isEqualTo("echo one\r\necho two");
    }

    @Test
    void shouldNormalizeLineEndingsToCrlf() throws Exception {
        Optional<Path> script = Process.writeWindowsCmdScript(List.of("cmd.exe", "/c", "echo one\necho two"), workingDirectory);

        assertThat(script).isPresent();
        assertThat(Files.readString(script.get())).isEqualTo("echo one\r\necho two");
    }

    @Test
    void shouldNotRewriteSingleLineCmdInvocation() throws Exception {
        assertThat(Process.writeWindowsCmdScript(List.of("cmd.exe", "/c", "echo one"), workingDirectory)).isEmpty();
    }

    @Test
    void shouldNotRewriteNonCmdInterpreter() throws Exception {
        assertThat(Process.writeWindowsCmdScript(List.of("/bin/sh", "-c", "echo one\necho two"), workingDirectory)).isEmpty();
        assertThat(Process.writeWindowsCmdScript(List.of("pwsh", "-Command", "echo one\necho two"), workingDirectory)).isEmpty();
        assertThat(Process.writeWindowsCmdScript(List.of("python", "-c", "print('a')\nprint('b')"), workingDirectory)).isEmpty();
    }

    @Test
    void shouldMatchCmdCaseInsensitivelyAndWithAnyPathOrQuotes() throws Exception {
        assertThat(Process.writeWindowsCmdScript(List.of("CMD.EXE", "/c", "a\r\nb"), workingDirectory)).isPresent();
        assertThat(Process.writeWindowsCmdScript(List.of("C:\\Windows\\System32\\cmd.exe", "/c", "a\r\nb"), workingDirectory)).isPresent();
        assertThat(Process.writeWindowsCmdScript(List.of("C:/Windows/System32/cmd.exe", "/c", "a\r\nb"), workingDirectory)).isPresent();
        assertThat(Process.writeWindowsCmdScript(List.of("\"C:\\Windows\\System32\\cmd.exe\"", "/c", "a\r\nb"), workingDirectory)).isPresent();
    }

    @Test
    void shouldNotRewriteIncompleteCmdInvocation() throws Exception {
        assertThat(Process.writeWindowsCmdScript(List.of(), workingDirectory)).isEmpty();
        assertThat(Process.writeWindowsCmdScript(List.of("cmd.exe"), workingDirectory)).isEmpty();
        assertThat(Process.writeWindowsCmdScript(List.of("cmd.exe", "/c"), workingDirectory)).isEmpty();
    }
}
