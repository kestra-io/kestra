package io.kestra.plugin.scripts.runner.docker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class DockerOutputFilesTest {
    @Inject
    TestRunContextFactory runContextFactory;

    @Test
    void shouldRejectParentReplacementBetweenValidationAndExtraction(@TempDir Path outside) throws Exception {
        var context = runContextFactory.of();
        Path root = context.workingDir().path();
        Files.createDirectory(root.resolve("sub"));
        Path sentinel = Files.writeString(outside.resolve("record.ion"), "sentinel");
        var permissions = Files.getPosixFilePermissions(sentinel);
        var entry = Mockito.spy(new TarArchiveEntry(context.workingDir().id() + "/sub/record.ion"));
        entry.setMode(0777);
        var replaced = new AtomicBoolean();
        Mockito.doAnswer(invocation ->
        {
            if (replaced.compareAndSet(false, true)) {
                Files.move(root.resolve("sub"), root.resolve("original"));
                Files.createSymbolicLink(root.resolve("sub"), outside);
            }
            return false;
        }).when(entry).isDirectory();
        var bytes = new ByteArrayInputStream("attacker content".getBytes());
        var tar = Mockito.mock(TarArchiveInputStream.class);
        Mockito.when(tar.getNextEntry()).thenReturn(entry, null);
        Mockito.when(tar.read(Mockito.any(byte[].class), Mockito.anyInt(), Mockito.anyInt()))
            .thenAnswer(invocation -> bytes.read(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

        assertThatThrownBy(() -> Docker.extractOutputFiles(tar, context, Set.of(Path.of("sub/record.ion"))))
            .isInstanceOf(IOException.class);
        assertThat(replaced).isTrue();
        assertThat(Files.readString(sentinel)).isEqualTo("sentinel");
        assertThat(Files.getPosixFilePermissions(sentinel)).isEqualTo(permissions);
    }

    @Test
    void shouldKeepWritesAndPermissionsInsideThePinnedParent(@TempDir Path outside) throws Exception {
        var context = runContextFactory.of();
        Path root = context.workingDir().path();
        Files.createDirectory(root.resolve("sub"));
        Path sentinel = Files.writeString(outside.resolve("record.ion"), "sentinel");
        var permissions = Files.getPosixFilePermissions(sentinel);
        var entry = new TarArchiveEntry(context.workingDir().id() + "/sub/record.ion");
        entry.setMode(0750);
        var tar = Mockito.mock(TarArchiveInputStream.class);
        Mockito.when(tar.getNextEntry()).thenReturn(entry, null);
        Mockito.when(tar.transferTo(Mockito.any())).thenAnswer(invocation ->
        {
            Files.move(root.resolve("sub"), root.resolve("original"));
            Files.createSymbolicLink(root.resolve("sub"), outside);
            return new ByteArrayInputStream("safe".getBytes()).transferTo(invocation.getArgument(0));
        });

        Docker.extractOutputFiles(tar, context, Set.of(Path.of("sub/record.ion")));

        assertThat(Files.readString(root.resolve("original/record.ion"))).isEqualTo("safe");
        assertThat(Files.getPosixFilePermissions(root.resolve("original/record.ion")))
            .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-x---"));
        assertThat(Files.readString(sentinel)).isEqualTo("sentinel");
        assertThat(Files.getPosixFilePermissions(sentinel)).isEqualTo(permissions);
    }

    @Test
    void shouldCreateNestedDirectoriesOverwriteFilesAndCleanStaging(@TempDir Path tempDir) throws Exception {
        var context = Mockito.spy(runContextFactory.of());
        var workingDir = new io.kestra.core.runners.LocalWorkingDir(tempDir, "task");
        Mockito.doReturn(workingDir).when(context).workingDir();
        Path existing = Files.createDirectories(workingDir.path().resolve("nested/deeper")).resolve("file");
        Files.writeString(existing, "old");
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(bytes)) {
            for (String filename : java.util.List.of("nested/deeper/file", "created/deeper/file")) {
                var entry = new TarArchiveEntry("task/" + filename);
                entry.setSize(3);
                entry.setMode(0750);
                output.putArchiveEntry(entry);
                output.write("new".getBytes());
                output.closeArchiveEntry();
            }
        }
        try (var tar = new TarArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Docker.extractOutputFiles(tar, context, Set.of());
        }
        assertThat(Files.readString(existing)).isEqualTo("new");
        assertThat(Files.readString(workingDir.path().resolve("created/deeper/file"))).isEqualTo("new");
        assertThat(Files.getPosixFilePermissions(existing))
            .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-x---"));
        try (var children = Files.list(tempDir)) {
            assertThat(children.map(path -> path.getFileName().toString())).containsExactly("task");
        }
    }

    @Test
    void shouldFailClosedWithoutSecureDirectoryStreams(@TempDir Path tempDir) throws Exception {
        try (var fs = java.nio.file.FileSystems.newFileSystem(java.net.URI.create("jar:" + tempDir.resolve("files.zip").toUri()), java.util.Map.of("create", "true"))) {
            Path root = Files.createDirectory(fs.getPath("/task"));
            var workingDir = Mockito.mock(io.kestra.core.runners.WorkingDir.class);
            Mockito.when(workingDir.path()).thenReturn(root);
            var context = Mockito.spy(runContextFactory.of());
            Mockito.doReturn(workingDir).when(context).workingDir();
            assertThatThrownBy(() -> Docker.extractOutputFiles(Mockito.mock(TarArchiveInputStream.class), context, Set.of(Path.of("record.ion"))))
                .isInstanceOf(IOException.class).hasMessageContaining("SecureDirectoryStream");
        }
    }

    @Test
    void shouldExtractOrdinaryOutputsWithoutSecureDirectoryStreams(@TempDir Path tempDir) throws Exception {
        try (var fs = java.nio.file.FileSystems.newFileSystem(java.net.URI.create("jar:" + tempDir.resolve("ordinary.zip").toUri()), java.util.Map.of("create", "true"))) {
            Path root = Files.createDirectory(fs.getPath("/task"));
            Files.writeString(Files.createDirectory(root.resolve("nested")).resolve("result.txt"), "old");
            var workingDir = Mockito.mock(io.kestra.core.runners.WorkingDir.class);
            Mockito.when(workingDir.path()).thenReturn(root);
            Mockito.when(workingDir.id()).thenReturn("task");
            var context = Mockito.spy(runContextFactory.of());
            Mockito.doReturn(workingDir).when(context).workingDir();
            try (var tar = archiveWithResult("task/nested/result.txt")) {
                Docker.extractOutputFiles(tar, context, Set.of());
            }

            assertThat(Files.readString(root.resolve("nested/result.txt"))).isEqualTo("result");
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = { '1', '2', '3', '4', '6' })
    void shouldSkipUnannouncedNonRegularArchiveEntries(int type) throws Exception {
        var context = runContextFactory.of();
        var special = new TarArchiveEntry(context.workingDir().id() + "/special", (byte) type);
        for (var announced : java.util.List.of(Set.<Path> of(), Set.of(Path.of("result.txt")))) {
            try (var tar = archiveWithResult(context.workingDir().id() + "/result.txt", special)) {
                Docker.extractOutputFiles(tar, context, announced);
            }
            assertThat(Files.exists(context.workingDir().path().resolve("special"))).isFalse();
            assertThat(Files.readString(context.workingDir().path().resolve("result.txt"))).isEqualTo("result");
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(ints = { '1', '2', '3', '4', '6' })
    void shouldRejectNonRegularAnnouncedTargetsAndParents(int type) throws Exception {
        var context = runContextFactory.of();
        for (String target : java.util.List.of("special", "special/record.ion")) {
            var entry = new TarArchiveEntry(context.workingDir().id() + "/special/", (byte) type);
            var tar = Mockito.mock(TarArchiveInputStream.class);
            Mockito.when(tar.getNextEntry()).thenReturn(entry, null);
            assertThatThrownBy(() -> Docker.extractOutputFiles(tar, context, Set.of(Path.of(target))))
                .isInstanceOf(IOException.class).hasMessageContaining("Announced output");
            assertThat(Files.exists(context.workingDir().path().resolve("special"))).isFalse();
        }
    }

    @Test
    void shouldRejectDirectoryAnnouncedAsAFile() throws Exception {
        var context = runContextFactory.of();
        var tar = Mockito.mock(TarArchiveInputStream.class);
        Mockito.when(tar.getNextEntry()).thenReturn(new TarArchiveEntry(context.workingDir().id() + "/directory/"), null);

        assertThatThrownBy(() -> Docker.extractOutputFiles(tar, context, Set.of(Path.of("directory"))))
            .isInstanceOf(IOException.class).hasMessageContaining("must be a regular archive file");
    }

    @Test
    void shouldRejectMissingAnnouncedTargetsWithoutAcceptingStaleHostFiles() throws Exception {
        var context = runContextFactory.of();
        Path stale = Files.writeString(context.workingDir().path().resolve("record.ion"), "stale");
        var tar = Mockito.mock(TarArchiveInputStream.class);

        assertThatThrownBy(() -> Docker.extractOutputFiles(tar, context, Set.of(Path.of("record.ion"))))
            .isInstanceOf(IOException.class).hasMessageContaining("not returned by Docker");
        assertThat(Files.readString(stale)).isEqualTo("stale");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(strings = { "task/../outside", "task//absolute" })
    void shouldRejectUnsafePathsWithoutAnnouncements(String name) throws Exception {
        var context = Mockito.spy(runContextFactory.of());
        var workingDir = Mockito.spy(context.workingDir());
        Mockito.doReturn("task").when(workingDir).id();
        Mockito.doReturn(workingDir).when(context).workingDir();
        var tar = Mockito.mock(TarArchiveInputStream.class);
        Mockito.when(tar.getNextEntry()).thenReturn(new TarArchiveEntry(name), null);

        assertThatThrownBy(() -> Docker.extractOutputFiles(tar, context, Set.of()))
            .isInstanceOf(IOException.class).hasMessageContaining("Container output paths");
    }

    @Test
    void shouldPreserveExistingFileAndRemoveStagingWhenArchiveReadFails(@TempDir Path tempDir) throws Exception {
        var context = Mockito.spy(runContextFactory.of());
        var workingDir = new io.kestra.core.runners.LocalWorkingDir(tempDir, "task");
        Mockito.doReturn(workingDir).when(context).workingDir();
        Path original = Files.writeString(workingDir.path().resolve("record.ion"), "original");
        var entry = new TarArchiveEntry("task/record.ion");
        var tar = Mockito.mock(TarArchiveInputStream.class);
        Mockito.when(tar.getNextEntry()).thenReturn(entry, null);
        var failure = new IOException("Truncated archive");
        Mockito.when(tar.transferTo(Mockito.any())).thenAnswer(invocation ->
        {
            java.io.OutputStream output = invocation.getArgument(0);
            output.write("partial".getBytes());
            throw failure;
        });

        assertThatThrownBy(() -> Docker.extractOutputFiles(tar, context, Set.of(Path.of("record.ion"))))
            .isSameAs(failure);
        assertThat(Files.readString(original)).isEqualTo("original");
        try (var children = Files.list(tempDir)) {
            assertThat(children.map(path -> path.getFileName().toString())).containsExactly("task");
        }
    }

    private static TarArchiveInputStream archiveWithResult(String filename, TarArchiveEntry... preceding) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(bytes)) {
            for (var entry : preceding) {
                output.putArchiveEntry(entry);
                output.closeArchiveEntry();
            }
            var entry = new TarArchiveEntry(filename);
            entry.setSize(6);
            output.putArchiveEntry(entry);
            output.write("result".getBytes());
            output.closeArchiveEntry();
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

}
