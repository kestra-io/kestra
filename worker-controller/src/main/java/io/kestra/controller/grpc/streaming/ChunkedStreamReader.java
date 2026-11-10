package io.kestra.controller.grpc.streaming;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import io.grpc.StatusRuntimeException;

/**
 * Reassembles a {@link StreamChunk} stream, into a local file or as it is read.
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

    /**
     * Makes the server-streaming call and returns its payload as a stream, holding one chunk at a time. Closing the
     * stream before its end cancels the call, rather than leaking it.
     *
     * @throws StatusRuntimeException if the call fails before its first chunk, which is how a server reports a
     *     missing payload.
     */
    public static InputStream open(Supplier<Iterator<StreamChunk>> call) {
        Context.CancellableContext context = Context.current().withCancellation();
        Context previous = context.attach();
        try {
            Iterator<StreamChunk> chunks = call.get();
            chunks.hasNext();
            return new ChunkInputStream(chunks, context);
        } catch (RuntimeException e) {
            context.cancel(null);
            throw e;
        } finally {
            context.detach(previous);
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

    private static final class ChunkInputStream extends InputStream {
        private final Iterator<StreamChunk> chunks;
        private final Context.CancellableContext context;
        private InputStream current = InputStream.nullInputStream();

        private ChunkInputStream(Iterator<StreamChunk> chunks, Context.CancellableContext context) {
            this.chunks = chunks;
            this.context = context;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            return read(single, 0, 1) == -1 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int read;
            while ((read = current.read(buffer, offset, length)) == -1) {
                if (!nextChunk()) {
                    return -1;
                }
            }
            return read;
        }

        @Override
        public void close() {
            context.cancel(null);
        }

        private boolean nextChunk() throws IOException {
            try {
                if (!chunks.hasNext()) {
                    return false;
                }
                current = chunks.next().getContent().newInput();
                return true;
            } catch (StatusRuntimeException e) {
                throw new IOException("Cannot read a payload streamed by the controller.", e);
            }
        }
    }
}
