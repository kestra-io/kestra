package io.kestra.plugin.scripts.exec.scripts.runners;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.DefaultLogConsumer;
import io.kestra.core.models.tasks.runners.RemoteRunnerInterface;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.tasks.runners.TaskRunnerDetailResult;
import io.kestra.core.models.tasks.runners.TaskRunnerResult;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Docker;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class CommandsWrapperOutputFilesTest {

    private static final Task TASK = new Task() {
        @Override
        public String getId() {
            return "test";
        }

        @Override
        public String getType() {
            return "test";
        }
    };

    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @ParameterizedTest
    @ValueSource(strings = { "direct", "normalized", "hardlink", "renamed", "case", "worker", "replaced" })
    void shouldRejectAnnouncedStagedInputsWithoutUploadingOrDeletingThem(String alias) throws Exception {
        var context = Mockito.spy(TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of()));
        var storage = Mockito.mock(io.kestra.core.storages.Storage.class);
        Mockito.doReturn(storage).when(context).storage();
        String sensitive = java.util.UUID.randomUUID().toString();
        String command = switch (alias) {
            case "normalized" -> "echo '::{\"outputFiles\":[\"./credential.txt\"]}::'";
            case "hardlink" -> "ln credential.txt alias.ion; echo '::{\"outputFiles\":[\"alias.ion\"]}::'";
            case "renamed" -> "mv credential.txt alias.ion; echo '::{\"outputFiles\":[\"alias.ion\"]}::'";
            case "replaced" -> "rm credential.txt; printf replacement > credential.txt; echo '::{\"outputFiles\":[\"credential.txt\"]}::'";
            case "case" -> "mv credential.txt CREDENTIAL.TXT; echo '::{\"outputFiles\":[\"CREDENTIAL.TXT\"]}::'";
            default -> "echo '::{\"outputFiles\":[\"credential.txt\"]}::'";
        };
        if (alias.equals("worker")) {
            Files.writeString(context.workingDir().path().resolve("credential.txt"), sensitive);
        }
        var commands = new CommandsWrapper(context).withTaskRunner(Process.instance())
            .withInputFiles(alias.equals("worker") ? null : Map.of("credential.txt", sensitive))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of(command)));

        assertThatThrownBy(commands::run).isInstanceOf(RunnableTaskException.class).hasCauseInstanceOf(IOException.class).hasMessageContaining("staged before command execution");
        Mockito.verifyNoInteractions(storage);
        String remaining = alias.equals("renamed") ? "alias.ion" : alias.equals("case") ? "CREDENTIAL.TXT" : "credential.txt";
        assertThat(Files.exists(context.workingDir().path().resolve(remaining))).isTrue();
        assertThat(Files.readString(context.workingDir().path().resolve(remaining)).equals(alias.equals("replaced") ? "replacement" : sensitive)).isTrue();
        if (alias.equals("hardlink")) {
            assertThat(Files.exists(context.workingDir().path().resolve("alias.ion"))).isTrue();
        }
    }

    @Test
    void shouldAllowGeneratedFilesAndExplicitlyConfiguredStagedInputs() throws Exception {
        var context = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var output = new CommandsWrapper(context).withTaskRunner(Process.instance())
            .withInputFiles(Map.of("input.txt", "explicit input"))
            .withOutputFiles(List.of("input.txt"))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("""
                filename=$(mktemp generated-XXXXXX.ion)
                printf '{id:1}' > "$filename"
                printf '::{"outputFiles":["%s"]}::\n' "$filename"
                """)))
            .run();
        assertThat(output.getOutputFiles()).hasSize(2).containsKey("input.txt");
        var generated = output.getOutputFiles().entrySet().stream().filter(entry -> !entry.getKey().equals("input.txt")).findFirst().orElseThrow();
        try (var input = context.storage().getFile(generated.getValue())) {
            assertThat(new String(input.readAllBytes())).isEqualTo("{id:1}");
        }
        assertThat(Files.exists(context.workingDir().path().resolve("input.txt"))).isFalse();
    }

    @Test
    void shouldPersistAnnouncedAndConfiguredFilesWithoutMutatingConfiguration() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        List<String> configured = List.of("*.txt", "dynamic.ion");
        ScriptOutput run = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withOutputFiles(configured)
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("""
                printf '{id:1}\\n{id:2}\\n' > dynamic.ion
                printf '{id:3}\\n' > second.ion
                echo configured > result.txt
                echo '::{"outputFiles":["dynamic.ion"]}::'
                echo '::{"outputFiles":["dynamic.ion","second.ion"],"outputs":{"kept":true}}::'
                """)))
            .run();

        assertThat(run.getOutputFiles()).containsOnlyKeys("dynamic.ion", "second.ion", "result.txt");
        assertThat(run.getVars()).containsEntry("kept", true);
        assertThat(configured).containsExactly("*.txt", "dynamic.ion");
        try (var input = storageInterface.get(TenantService.MAIN_TENANT, null, run.getOutputFiles().get("dynamic.ion"))) {
            assertThat(new String(input.readAllBytes())).isEqualTo("{id:1}\n{id:2}\n");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "../escape.ion", "/etc/passwd", "missing.ion", ".", "sub/../record.ion", ".kestra-execution-context.json" })
    void shouldRejectInvalidAnnouncements(String filename) throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var commands = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo '::{\"outputFiles\":[\"" + filename + "\"]}::'")));

        assertThatThrownBy(commands::run).isInstanceOf(Exception.class);
    }

    @Test
    void shouldRejectSymlinkEscapes() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var outside = Files.createTempFile("outside", ".ion");
        try {
            Files.createSymbolicLink(runContext.workingDir().path().resolve("escape.ion"), outside);
            var commands = new CommandsWrapper(runContext)
                .withTaskRunner(Process.instance())
                .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
                .withCommands(Property.ofValue(List.of("echo '::{\"outputFiles\":[\"escape.ion\"]}::'")));

            assertThatThrownBy(commands::run).isInstanceOf(Exception.class);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void shouldTreatAnnouncedPathsAsLiteralFiles() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        String filename = "{{1+1}}.ion";
        var logConsumer = new DefaultLogConsumer(runContext);
        logConsumer.accept("::{\"outputFiles\":[\"" + filename + "\"]}::", false);

        var output = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withLogConsumer(logConsumer)
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("filename='{''{1+1}''}.ion'; printf '{id:1}' > \"$filename\"")))
            .run();

        assertThat(output.getOutputFiles()).containsOnlyKeys(filename);
    }

    @ParameterizedTest
    @ValueSource(strings = { "missing.ion", "directory.ion" })
    void shouldPreserveSuccessfulCommandMetadataWhenCollectionFails(String filename) throws Exception {
        var context = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var commands = new CommandsWrapper(context).withTaskRunner(Process.instance()).withEnableOutputDirectory(true)
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")));
        String directory = commands.getOutputDirectory().getFileName().toString();
        commands = commands.withCommands(Property.ofValue(List.of("""
            mkdir directory.ion
            printf '{id:1}' > '%s/persisted.ion'
            echo stdout
            echo diagnostic >&2
            echo '::{"outputFiles":["%s"],"outputs":{"kept":true}}::'
            """.formatted(directory, filename))));

        assertThatThrownBy(commands::run).isInstanceOfSatisfying(RunnableTaskException.class, exception ->
        {
            assertThat(exception).hasMessageContaining("output file collection failed").hasMessageContaining(filename);
            assertThat(exception.getCause()).isInstanceOf(IOException.class);
            var output = (ScriptOutput) exception.getOutput();
            assertThat(output.getExitCode()).isZero();
            assertThat(output.getVars()).containsEntry("kept", true);
            assertThat(output.getStdOutLineCount()).isEqualTo(2);
            assertThat(output.getStdErrLineCount()).isEqualTo(1);
            assertThat(output.getOutputFiles()).containsOnlyKeys("persisted.ion");
            assertThat(context.storage().isFileExist(output.getOutputFiles().get("persisted.ion"))).isTrue();
        });
    }

    @Test
    void shouldCollectAnnouncedFilesWhenTheCommandFails() throws Exception {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var commands = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("""
                echo '{id:1}' > partial.ion
                echo '::{"outputFiles":["partial.ion"],"outputs":{"kept":true}}::'
                exit 1
                """)));

        assertThatThrownBy(commands::run).isInstanceOfSatisfying(RunnableTaskException.class, exception ->
        {
            var output = (ScriptOutput) exception.getOutput();
            assertThat(output.getOutputFiles()).containsOnlyKeys("partial.ion");
            assertThat(output.getVars()).containsEntry("kept", true);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shouldUploadOutputDirectoryOverlapOnceAndPreserveKeysAndPrecedence(boolean announce) throws Exception {
        RunContext context = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var commands = new CommandsWrapper(context)
            .withTaskRunner(Process.instance())
            .withEnableOutputDirectory(true)
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")));
        String directory = commands.getOutputDirectory().getFileName().toString();
        var output = commands.withOutputFiles(List.of("**/*.ion", "shared.ion"))
            .withCommands(Property.ofValue(List.of("""
                echo directory > '%s/shared.ion'
                echo nested > '%s/only.ion'
                echo root > shared.ion
                %s
                """.formatted(directory, directory, announce ? "echo '::{\"outputFiles\":[\"" + directory + "/shared.ion\",\"" + directory + "/only.ion\"]}::'" : "true"))))
            .run();

        assertThat(output.getOutputFiles()).containsOnlyKeys("shared.ion", "only.ion", directory + "/shared.ion", directory + "/only.ion");
        assertThat(output.getOutputFiles().get("only.ion")).isEqualTo(output.getOutputFiles().get(directory + "/only.ion"));
        assertThat(output.getOutputFiles().get("only.ion").getPath()).endsWith("/only.ion");
        try (var input = context.storage().getFile(output.getOutputFiles().get("shared.ion"))) {
            assertThat(new String(input.readAllBytes())).isEqualTo("root\n");
        }
        try (var input = context.storage().getFile(output.getOutputFiles().get(directory + "/shared.ion"))) {
            assertThat(new String(input.readAllBytes())).isEqualTo("directory\n");
        }
    }

    @Test
    void shouldPreserveCommandFailureWhenAnnouncedFileDisappears() throws Exception {
        RunContext context = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var commands = new CommandsWrapper(context)
            .withTaskRunner(Process.instance())
            .withEnableOutputDirectory(true)
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("""
                echo '::{"outputs":{"checkpoint":1}}::'
                echo '{id:1}' > record.ion
                echo '::{"outputFiles":["record.ion"]}::'
                rm record.ion
                echo diagnostic >&2
                exit 7
                """)));

        Files.writeString(commands.getOutputDirectory().resolve("persisted.ion"), "{id:2}");
        assertThatThrownBy(commands::run).isInstanceOfSatisfying(RunnableTaskException.class, exception ->
        {
            assertThat(exception.getCause()).isInstanceOfSatisfying(TaskException.class, cause ->
            {
                assertThat(cause.getExitCode()).isEqualTo(7);
                assertThat(cause.getSuppressed()).hasSize(1);
                assertThat(cause.getSuppressed()[0]).isInstanceOf(IOException.class);
            });
            var output = (ScriptOutput) exception.getOutput();
            assertThat(output.getExitCode()).isEqualTo(7);
            assertThat(output.getVars()).containsEntry("checkpoint", 1);
            assertThat(output.getStdOutLineCount()).isEqualTo(2);
            assertThat(output.getStdErrLineCount()).isEqualTo(1);
            assertThat(output.getOutputFiles()).containsOnlyKeys("persisted.ion");
            assertThat(context.storage().isFileExist(output.getOutputFiles().get("persisted.ion"))).isTrue();
        });
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = { 0, 7 })
    void shouldPreserveTraversalFailureAndCommandMetadata(int exitCode) throws Exception {
        var context = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var commands = new CommandsWrapper(context).withTaskRunner(Process.instance()).withEnableOutputDirectory(true)
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo '::{\"outputs\":{\"checkpoint\":1}}::'; echo stdout; echo stderr >&2; exit " + exitCode)));
        var directory = commands.getOutputDirectory();
        var failure = new IOException("Traversal failed after the walk began");
        var visited = new java.util.concurrent.atomic.AtomicBoolean();
        try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.walk(directory)).thenAnswer(
                invocation -> java.util.stream.Stream.of(directory, directory.resolve("child"))
                    .peek(path ->
                    {
                        if (path.equals(directory)) {
                            visited.set(true);
                        } else {
                            throw new UncheckedIOException(failure);
                        }
                    })
            );
            if (exitCode == 7) {
                assertThatThrownBy(commands::run).isInstanceOfSatisfying(RunnableTaskException.class, exception ->
                {
                    assertThat(exception.getCause()).isInstanceOf(TaskException.class);
                    assertThat(exception.getCause().getSuppressed()).containsExactly(failure);
                    var output = (ScriptOutput) exception.getOutput();
                    assertThat(output.getExitCode()).isEqualTo(7);
                    assertThat(output.getVars()).containsEntry("checkpoint", 1);
                    assertThat(output.getStdOutLineCount()).isEqualTo(2);
                    assertThat(output.getStdErrLineCount()).isEqualTo(1);
                    assertThat(output.getOutputFiles()).isEmpty();
                });
            } else {
                assertThatThrownBy(commands::run).isInstanceOfSatisfying(RunnableTaskException.class, exception ->
                {
                    assertThat(exception.getCause()).isSameAs(failure);
                    var output = (ScriptOutput) exception.getOutput();
                    assertThat(output.getExitCode()).isZero();
                    assertThat(output.getVars()).containsEntry("checkpoint", 1);
                    assertThat(output.getStdOutLineCount()).isEqualTo(2);
                    assertThat(output.getStdErrLineCount()).isEqualTo(1);
                    assertThat(output.getOutputFiles()).isEmpty();
                });
            }
        }
        assertThat(visited).isTrue();
    }

    @Test
    void shouldRejectRemoteAnnouncementsWithoutPublishingStaleInputs() throws Exception {
        RunContext context = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        TaskRunner<?> runner = Mockito.mock(TaskRunner.class, Mockito.withSettings().extraInterfaces(RemoteRunnerInterface.class));
        Mockito.when(runner.additionalVars(Mockito.any(), Mockito.any())).thenReturn(new HashMap<>());
        Mockito.when(((RemoteRunnerInterface) runner).getSyncWorkingDirectory()).thenReturn(Property.ofValue(false));
        Mockito.doAnswer(invocation ->
        {
            var commands = (CommandsWrapper) invocation.getArgument(1);
            if (commands.getOutputFiles() == null) {
                commands.getLogConsumer().accept("::{\"outputFiles\":[\"record.ion\"]}::", false);
            }
            return new TaskRunnerResult<TaskRunnerDetailResult>(0, commands.getLogConsumer());
        }).when(runner).run(Mockito.any(), Mockito.any(), Mockito.any());
        var commands = new CommandsWrapper(context)
            .withTaskRunner(runner)
            .withInputFiles(Map.of("record.ion", "stale host input"))
            .withCommands(Property.ofValue(List.of("remote command")));

        assertThatThrownBy(commands::run).isInstanceOf(RunnableTaskException.class).hasCauseInstanceOf(IOException.class).hasMessageContaining("Dynamic output files");
        assertThat(Files.readString(context.workingDir().path().resolve("record.ion"))).isEqualTo("stale host input");
        var configured = new CommandsWrapper(context).withTaskRunner(runner)
            .withOutputFiles(List.of("record.ion"))
            .withCommands(Property.ofValue(List.of("remote command"))).run();
        assertThat(configured.getOutputFiles()).containsOnlyKeys("record.ion");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shouldRejectUnverifiedDockerTransfers(boolean wait) throws Exception {
        RunContext context = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        Docker runner = Mockito.mock(Docker.class);
        Mockito.when(runner.getWait()).thenReturn(Property.ofValue(wait));
        Mockito.when(runner.getFileHandlingStrategy()).thenReturn(Property.ofValue(wait ? Docker.FileHandlingStrategy.MOUNT : Docker.FileHandlingStrategy.VOLUME));
        Mockito.when(runner.additionalVars(Mockito.any(), Mockito.any())).thenReturn(new HashMap<>());
        Mockito.doAnswer(invocation ->
        {
            var commands = (CommandsWrapper) invocation.getArgument(1);
            commands.getLogConsumer().accept("::{\"outputFiles\":[\"record.ion\"],\"outputs\":{\"kept\":true}}::", false);
            commands.getLogConsumer().accept("stdout", false);
            commands.getLogConsumer().accept("diagnostic", true);
            return new TaskRunnerResult<TaskRunnerDetailResult>(0, commands.getLogConsumer());
        }).when(runner).run(Mockito.any(), Mockito.any(), Mockito.any());
        var commands = new CommandsWrapper(context).withTaskRunner(runner)
            .withInputFiles(Map.of("record.ion", "stale"))
            .withCommands(Property.ofValue(List.of("command")));

        assertThatThrownBy(commands::run).isInstanceOfSatisfying(RunnableTaskException.class, exception ->
        {
            assertThat(exception).hasMessageContaining("output file collection failed").hasMessageContaining("synchronous Docker runner using VOLUME");
            assertThat(exception.getCause()).isInstanceOf(IOException.class);
            var output = (ScriptOutput) exception.getOutput();
            assertThat(output.getExitCode()).isZero();
            assertThat(output.getVars()).containsEntry("kept", true);
            assertThat(output.getStdOutLineCount()).isEqualTo(2);
            assertThat(output.getStdErrLineCount()).isEqualTo(1);
            assertThat(output.getOutputFiles()).isEmpty();
        });
        assertThat(Files.readString(context.workingDir().path().resolve("record.ion"))).isEqualTo("stale");
    }

    @Test
    void shouldRejectNullAnnouncements() {
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        var commands = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo '::{\"outputFiles\":[null]}::'")));

        assertThatThrownBy(commands::run).isInstanceOf(RunnableTaskException.class).hasCauseInstanceOf(IOException.class).hasMessageContaining("must not be null");
    }

    @Test
    void shouldResolveOutputFilesPebbleExpressionInScript() throws Exception {
        // Given
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());

        // Use Property.ofExpression to simulate YAML deserialization (value=null → Pebble rendering runs).
        // Dot-notation {{ outputFiles.outfile }} avoids nested quote escaping in the JSON expression.
        // When — script writes the resolved Pebble path to 'outfile' so the content proves resolution.
        ScriptOutput run = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withOutputFiles(List.of("outfile"))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.<List<String>> ofExpression("[\"printf '%s' {{ outputFiles.outfile }} > outfile\"]"))
            .run();

        // Then
        assertThat(run.getExitCode()).isEqualTo(0);
        assertThat(run.getOutputFiles()).containsKey("outfile");
        String content = new String(storageInterface.get(TenantService.MAIN_TENANT, null, run.getOutputFiles().get("outfile")).readAllBytes());
        // The file content should be the resolved absolute path ending with /outfile
        assertThat(content).endsWith("/outfile");
    }

    @Test
    void shouldStillCollectGlobOutputFilesAfterExecution() throws Exception {
        // Given — glob patterns are NOT injected into Pebble context but are still collected post-run
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());

        // When
        ScriptOutput run = new CommandsWrapper(runContext)
            .withTaskRunner(Process.instance())
            .withOutputFiles(List.of("*.txt"))
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("echo hello > result.txt")))
            .run();

        // Then — glob still collected even though it was not in the Pebble context
        assertThat(run.getExitCode()).isEqualTo(0);
        assertThat(run.getOutputFiles()).containsKey("result.txt");
    }
}
