package io.kestra.controller.grpc.streaming;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.protobuf.ByteString;

import io.kestra.controller.grpc.StreamChunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChunkedStreamReaderTest {

    @Test
    void shouldReassembleTheChunksIntoTheTarget(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("nested").resolve("artifact.jar");

        ChunkedStreamReader.writeTo(() -> chunks("chunk-one", "chunk-two"), target, 18);

        assertThat(Files.readString(target)).isEqualTo("chunk-onechunk-two");
    }

    @Test
    void shouldRejectATransferShorterThanAnnouncedAndLeaveNothingBehind(@TempDir Path directory) {
        Path target = directory.resolve("artifact.jar");

        assertThatThrownBy(() -> ChunkedStreamReader.writeTo(() -> chunks("truncated"), target, 512))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("expected 512 bytes but received 9");

        assertThat(target).doesNotExist();
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void shouldRejectATransferLongerThanAnnouncedAndLeaveNothingBehind(@TempDir Path directory) {
        Path target = directory.resolve("artifact.jar");

        assertThatThrownBy(() -> ChunkedStreamReader.writeTo(() -> chunks("chunk-one", "chunk-two"), target, 9))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("more than the announced 9 bytes");

        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void shouldCreateTheTargetWithTheSamePermissionsAsAPlainFile(@TempDir Path directory) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path target = directory.resolve("artifact.jar");
        Path reference = Files.createFile(directory.resolve("reference.jar"));

        ChunkedStreamReader.writeTo(() -> chunks("payload"), target, 7);

        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(Files.getPosixFilePermissions(reference));
    }

    private static Iterator<StreamChunk> chunks(String... contents) {
        List<StreamChunk> chunks = Arrays.stream(contents)
            .map(content -> StreamChunk.newBuilder().setContent(ByteString.copyFrom(content, StandardCharsets.UTF_8)).build())
            .toList();
        return chunks.iterator();
    }
}
