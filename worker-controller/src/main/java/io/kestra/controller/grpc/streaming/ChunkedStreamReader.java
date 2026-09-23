package io.kestra.controller.grpc.streaming;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.protobuf.ByteString;

import io.kestra.controller.grpc.StreamChunk;

import io.grpc.Context;

/**
 * Reassembles a {@link StreamChunk} stream into a local file.
 */
public final class ChunkedStreamReader {

    private ChunkedStreamReader() {
    }

    /**
     * Writes the chunks to a temporary file next to the target and moves it in place, so that a
     * truncated or failed transfer never leaves a partial file under the target name.
     * <p>
     * The call is made here rather than passed in as its iterator, because a blocking server-streaming
     * iterator leaks its call, and whatever the server streams it from, unless it is read to the end —
     * which neither an oversized payload nor a failure to write locally does. Owning the call is what
     * lets it be cancelled on those paths.
     *
     * @param call makes the server-streaming call and returns its iterator.
     * @param expectedSize the size the payload is announced to have.
     * @throws IOException if the transfer fails, or delivers a number of bytes other than {@code expectedSize}.
     */
    public static void writeTo(Supplier<Iterator<StreamChunk>> call, Path target, long expectedSize) throws IOException {
        Context.CancellableContext context = Context.current().withCancellation();
        Context previous = context.attach();
        try {
            writeTo(call.get(), target, expectedSize);
        } finally {
            // A no-op once the call has completed on its own.
            context.detachAndCancel(previous, null);
        }
    }

    private static void writeTo(Iterator<StreamChunk> chunks, Path target, long expectedSize) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path directory = absoluteTarget.getParent();
        Files.createDirectories(directory);

        // Not createTempFile: it creates the file readable by its owner alone, and the move below
        // carries those permissions to the target instead of the umask-derived ones.
        Path partial = directory.resolve("%s.%s.part".formatted(absoluteTarget.getFileName(), UUID.randomUUID()));
        try {
            long written = 0;
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW))) {
                while (chunks.hasNext()) {
                    ByteString content = chunks.next().getContent();
                    written += content.size();
                    if (written > expectedSize) {
                        throw new IOException(
                            "Oversized transfer of '%s': more than the announced %d bytes were received.".formatted(absoluteTarget.getFileName(), expectedSize)
                        );
                    }
                    content.writeTo(output);
                }
            }

            if (written != expectedSize) {
                throw new IOException(
                    "Incomplete transfer of '%s': expected %d bytes but received %d.".formatted(absoluteTarget.getFileName(), expectedSize, written)
                );
            }

            Files.move(partial, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
    }
}
