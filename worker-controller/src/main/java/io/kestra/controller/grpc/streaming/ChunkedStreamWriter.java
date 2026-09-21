package io.kestra.controller.grpc.streaming;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.controller.grpc.StreamChunk;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * Streams an {@link InputStream} to a gRPC client as {@link StreamChunk} messages.
 * <p>
 * Chunks are only produced while the transport reports itself ready, so a single chunk per call is
 * held in memory whatever the size of the payload. Writing the whole payload eagerly would instead
 * buffer it per concurrent call, and a plugin resync is broadcast to the whole worker fleet at once.
 */
public final class ChunkedStreamWriter {

    /**
     * Small enough to stay well below the default {@code kestra.grpc.max-inbound-message-size} of
     * 10 MB on both ends, rather than being negotiated per call.
     */
    public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(ChunkedStreamWriter.class);

    private final InputStream source;
    private final ServerCallStreamObserver<StreamChunk> observer;
    private final RequestOrResponseHeader header;
    private final byte[] buffer;

    private final AtomicBoolean writing = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Streams the source to the client and closes it, using {@link #DEFAULT_CHUNK_SIZE}.
     *
     * @param source the payload, closed once fully written, on error and on cancellation.
     */
    public static void write(InputStream source, StreamObserver<StreamChunk> observer, RequestOrResponseHeader header) {
        write(source, observer, header, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Streams the source to the client and closes it.
     *
     * @param source the payload, closed once fully written, on error and on cancellation.
     * @param chunkSize the maximum number of bytes per message.
     * @throws IllegalArgumentException if the observer does not belong to a server-streaming call.
     */
    public static void write(InputStream source, StreamObserver<StreamChunk> observer, RequestOrResponseHeader header, int chunkSize) {
        if (!(observer instanceof ServerCallStreamObserver<StreamChunk> serverObserver)) {
            close(source);
            throw new IllegalArgumentException(
                "Cannot stream chunks to a '%s': only the observer of a server-streaming call reports readiness.".formatted(observer.getClass().getName())
            );
        }
        new ChunkedStreamWriter(source, serverObserver, header, chunkSize).start();
    }

    private ChunkedStreamWriter(InputStream source, ServerCallStreamObserver<StreamChunk> observer, RequestOrResponseHeader header, int chunkSize) {
        this.source = source;
        this.observer = observer;
        this.header = header;
        this.buffer = new byte[chunkSize];
    }

    private void start() {
        observer.setOnCancelHandler(this::cancel);
        observer.setOnReadyHandler(this::write);
        write();
    }

    private void write() {
        while (!terminated.get()) {
            if (!writing.compareAndSet(false, true)) {
                return;
            }
            try {
                writeWhileReady();
            } finally {
                // The guard is released before the flag is read, and cancel() sets the flag before
                // claiming the guard, so at least one of the two sees the other and the source is
                // closed exactly once, never while a read is in flight.
                writing.set(false);
                if (terminated.get()) {
                    closeSource();
                }
            }

            // A readiness callback raised while the loop above was running returned immediately, so
            // readiness is re-read here rather than waiting for a callback that will not come again.
            if (terminated.get() || !observer.isReady()) {
                return;
            }
        }
    }

    private void writeWhileReady() {
        try {
            while (!terminated.get() && observer.isReady()) {
                int read = source.read(buffer);
                if (read == -1) {
                    complete();
                    return;
                }
                observer.onNext(
                    StreamChunk.newBuilder()
                        .setHeader(header)
                        .setContent(ByteString.copyFrom(buffer, 0, read))
                        .build()
                );
            }
        } catch (Exception e) {
            fail(e);
        }
    }

    private void complete() {
        if (terminated.compareAndSet(false, true)) {
            observer.onCompleted();
        }
    }

    private void fail(Exception e) {
        if (terminated.compareAndSet(false, true)) {
            observer.onError(e);
        }
    }

    private void cancel() {
        terminated.set(true);
        if (writing.compareAndSet(false, true)) {
            try {
                closeSource();
            } finally {
                writing.set(false);
            }
        }
    }

    private void closeSource() {
        if (closed.compareAndSet(false, true)) {
            close(source);
        }
    }

    private static void close(InputStream source) {
        try {
            source.close();
        } catch (IOException e) {
            log.warn("Failed to close the source of a streamed response.", e);
        }
    }
}
