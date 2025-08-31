package io.kestra.core.runners;

import io.kestra.core.models.tasks.FileExistComportment;
import io.kestra.core.utils.IdUtils;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class LocalWorkingDirTest {

    @Test
    void shouldReturnWorkingDirPathGivenWorkingDirId() {
        String workingDirId = IdUtils.create();
        LocalWorkingDir workingDirectory = new LocalWorkingDir(Path.of("/tmp"), workingDirId);
        assertTrue(workingDirectory.path().endsWith(workingDirId));
    }

    @Test
    void shouldReturnTheSameWorkingDirPath() {
        LocalWorkingDir workingDirectory = new LocalWorkingDir(Path.of("/tmp"), IdUtils.create());
        assertThat(workingDirectory.path()).isEqualTo(workingDirectory.path());
    }

    @Test
    void shouldResolvePathFromWorkingDir() {
        LocalWorkingDir workingDirectory = new LocalWorkingDir(Path.of("/tmp"), IdUtils.create());

        Path path = workingDirectory.resolve(Path.of("file.txt"));
        assertThat(path.toString()).isEqualTo(workingDirectory.path() + "/file.txt");

        path = workingDirectory.resolve(Path.of("subdir/file.txt"));
        assertThat(path.toString()).isEqualTo(workingDirectory.path() + "/subdir/file.txt");

        assertThat(workingDirectory.resolve(null)).isEqualTo(workingDirectory.path());
        assertThrows(IllegalArgumentException.class, () -> workingDirectory.resolve(Path.of("/etc/passwd")));
        assertThrows(IllegalArgumentException.class, () -> workingDirectory.resolve(Path.of("../../etc/passwd")));
        assertThrows(IllegalArgumentException.class, () -> workingDirectory.resolve(Path.of("subdir/../../../etc/passwd")));
    }

    @Test
    void shouldCreatedTempFile() throws IOException {
        String workingDirId = IdUtils.create();
        TestWorkingDir workingDirectory = new TestWorkingDir(workingDirId, new LocalWorkingDir(Path.of("/tmp/sub/dir/tmp/"), workingDirId));
        Path tempFile = workingDirectory.createTempFile();
        assertThat(tempFile.toFile().getAbsolutePath().startsWith("/tmp/sub/dir/tmp/")).isTrue();
        assertThat(workingDirectory.getAllCreatedTempFiles().size()).isEqualTo(1);
    }

    @Test
    void shouldCreateFile() throws IOException {
        String workingDirId = IdUtils.create();
        TestWorkingDir workingDirectory = new TestWorkingDir(workingDirId, new LocalWorkingDir(Path.of("/tmp/sub/dir/tmp/"), workingDirId));
        Path path = workingDirectory.createFile("folder/file.txt");

        assertThat(path.toFile().getAbsolutePath().startsWith("/tmp/sub/dir/tmp/")).isTrue();
        assertThat(path.toFile().getAbsolutePath().endsWith("/folder/file.txt")).isTrue();
        assertThat(workingDirectory.getAllCreatedFiles().size()).isEqualTo(1);
    }

    @Test
    void shouldThrowExceptionGivenFileAlreadyExist() throws IOException {
        String workingDirId = IdUtils.create();
        TestWorkingDir workingDirectory = new TestWorkingDir(workingDirId, new LocalWorkingDir(Path.of("/tmp/sub/dir/tmp/"), workingDirId));

        workingDirectory.createFile("folder/file.txt", "1".getBytes(StandardCharsets.UTF_8));
        Assertions.assertThrows(FileAlreadyExistsException.class, () -> {
            workingDirectory.createFile("folder/file.txt", "2".getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    void shouldFindAllFilesMatchingPatterns() throws IOException {
        // Given
        LocalWorkingDir workingDir = new LocalWorkingDir(Path.of("/tmp/"));
        workingDir.createTempFile();
        workingDir.createFile("test1.txt");
        workingDir.createFile("test2.txt");
        workingDir.createFile("sub/test3.txt");
        workingDir.createFile("sub/dir/test4.txt");

        // When - Then

        // glob
        assertThat(workingDir.findAllFilesMatching(List.of("glob:**/*.*")).size()).isEqualTo(5);
        // pattern
        assertThat(workingDir.findAllFilesMatching(List.of("*.*", "**/*.*")).size()).isEqualTo(5);
        // duplicate pattern
        assertThat(workingDir.findAllFilesMatching(List.of("*.*", "**/*.*", "**/*.*")).size()).isEqualTo(5);
        // regex
        assertThat(workingDir.findAllFilesMatching(List.of("regex:.*\\.tmp", "*.txt", "**/*.txt")).size()).isEqualTo(5);
    }

    @Test
    void shouldRecreateDirectoryAfterCleanup() throws IOException {
        // Given
        LocalWorkingDir workingDir = new LocalWorkingDir(Path.of("/tmp/"), IdUtils.create());
        Path firtPath = workingDir.path(true);
        Path file = workingDir.createFile("test.txt");

        // When
        workingDir.cleanup();

        // Then
        assertThat(file.toFile().exists()).isFalse();
        assertThat(firtPath.toFile().exists()).isFalse();

        // When
        Path secondPath = workingDir.path(true);
        // Then
        assertThat(secondPath.toFile().exists()).isTrue();
        assertThat(firtPath).isEqualTo(secondPath);
    }

    @Test
    void should_put_file_into_local_dir() throws IOException {
        LocalWorkingDir workingDir = new LocalWorkingDir(Path.of("/tmp/"), IdUtils.create());
        workingDir.path(true);
        Path file = workingDir.createFile("test.txt", new ByteArrayInputStream("First file".getBytes(StandardCharsets.UTF_8)));

        assertThrows(FileAlreadyExistsException.class, () -> workingDir.putFile(file, new ByteArrayInputStream("Hello world".getBytes(StandardCharsets.UTF_8)), FileExistComportment.FAIL));
        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).isEqualTo(List.of("First file"));

        workingDir.putFile(file, new ByteArrayInputStream("Hello world".getBytes(StandardCharsets.UTF_8)), FileExistComportment.IGNORE);
        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).isEqualTo(List.of("First file"));

        workingDir.putFile(file, new ByteArrayInputStream("Hello world".getBytes(StandardCharsets.UTF_8)), FileExistComportment.WARN);
        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).isEqualTo(List.of("First file"));

        workingDir.putFile(file, new ByteArrayInputStream("New file".getBytes(StandardCharsets.UTF_8)), FileExistComportment.OVERWRITE);
        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8)).isEqualTo(List.of("New file"));
    }
}