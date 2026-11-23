package io.kestra.controller.grpc.streaming;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;

import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.controller.grpc.StreamChunk;

import io.grpc.stub.ServerCallStreamObserver;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkedStreamWriterTest {

    private static final RequestOrResponseHeader HEADER = RequestOrResponseHeader.newBuilder().setClientId("controller").build();

    @Test
    void shouldWriteTheWholePayloadWhileTheTransportStaysReady() {
        byte[] payload = "a payload larger than one chunk".getBytes(StandardCharsets.UTF_8);
        RecordingObserver observer = new RecordingObserver(false);

        ChunkedStreamWriter.write(new ByteArrayInputStream(payload), observer, HEADER, 8);

        assertThat(observer.written()).isEqualTo(payload);
        assertThat(observer.chunks).hasSize(4);
        assertThat(observer.isCompleted).isTrue();
    }

    @Test
    void shouldWriteOneChunkPerReadinessSignalWhenTheTransportIsNotReady() {
        byte[] payload = "0123456789ab".getBytes(StandardCharsets.UTF_8);
        RecordingObserver observer = new RecordingObserver(true);

        ChunkedStreamWriter.write(new ByteArrayInputStream(payload), observer, HEADER, 4);

        // The transport takes one chunk, then only accepts the next one once it says so again.
        assertThat(observer.chunks).hasSize(1);

        observer.becomeReady();
        assertThat(observer.chunks).hasSize(2);

        observer.becomeReady();
        assertThat(observer.chunks).hasSize(3);
        assertThat(observer.isCompleted).isFalse();

        observer.becomeReady();
        assertThat(observer.written()).isEqualTo(payload);
        assertThat(observer.isCompleted).isTrue();
    }

    @Test
    void shouldCloseTheSourceOnceTheWholePayloadIsWritten() {
        ClosingInputStream source = new ClosingInputStream("payload".getBytes(StandardCharsets.UTF_8));

        ChunkedStreamWriter.write(source, new RecordingObserver(false), HEADER, 4);

        assertThat(source.isClosed).isTrue();
    }

    @Test
    void shouldFailTheCallAndCloseTheSourceWhenItCannotBeRead() {
        RecordingObserver observer = new RecordingObserver(false);
        ClosingInputStream source = new ClosingInputStream("payload".getBytes(StandardCharsets.UTF_8));
        source.failOnRead = true;

        ChunkedStreamWriter.write(source, observer, HEADER, 4);

        assertThat(observer.error).isInstanceOf(IOException.class);
        assertThat(observer.isCompleted).isFalse();
        assertThat(source.isClosed).isTrue();
    }

    @Test
    void shouldCloseTheSourceAndStopWritingWhenTheClientCancels() {
        RecordingObserver observer = new RecordingObserver(true);
        ClosingInputStream source = new ClosingInputStream("0123456789ab".getBytes(StandardCharsets.UTF_8));

        ChunkedStreamWriter.write(source, observer, HEADER, 4);
        observer.cancel();
        observer.becomeReady();

        assertThat(observer.chunks).hasSize(1);
        assertThat(observer.isCompleted).isFalse();
        assertThat(source.isClosed).isTrue();
    }

    @Test
    void shouldNotCloseTheSourceWhileAReadIsInFlightWhenTheClientCancels() {
        RecordingObserver observer = new RecordingObserver(false);
        ClosingInputStream source = new ClosingInputStream("0123456789ab".getBytes(StandardCharsets.UTF_8));
        source.onRead = observer::cancel;

        ChunkedStreamWriter.write(source, observer, HEADER, 4);

        assertThat(source.wasClosedDuringRead).isFalse();
        assertThat(source.isClosed).isTrue();
        assertThat(observer.isCompleted).isFalse();
    }

    private static final class RecordingObserver extends ServerCallStreamObserver<StreamChunk> {

        private final List<ByteString> chunks = new ArrayList<>();
        private final boolean pausesAfterEachChunk;

        private Runnable onReadyHandler;
        private Runnable onCancelHandler;
        private boolean isReady = true;
        private boolean isCancelled = false;
        private boolean isCompleted = false;
        private Throwable error;

        private RecordingObserver(boolean pausesAfterEachChunk) {
            this.pausesAfterEachChunk = pausesAfterEachChunk;
        }

        void becomeReady() {
            isReady = true;
            onReadyHandler.run();
        }

        void cancel() {
            isCancelled = true;
            onCancelHandler.run();
        }

        byte[] written() {
            return chunks.stream().reduce(ByteString.EMPTY, ByteString::concat).toByteArray();
        }

        @Override
        public boolean isReady() {
            return isReady;
        }

        @Override
        public void setOnReadyHandler(Runnable onReadyHandler) {
            this.onReadyHandler = onReadyHandler;
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        @Override
        public void setOnCancelHandler(Runnable onCancelHandler) {
            this.onCancelHandler = onCancelHandler;
        }

        @Override
        public void disableAutoInboundFlowControl() {
        }

        @Override
        public void request(int count) {
        }

        @Override
        public void setMessageCompression(boolean enable) {
        }

        @Override
        public void setCompression(String compression) {
        }

        @Override
        public void onNext(StreamChunk value) {
            chunks.add(value.getContent());
            if (pausesAfterEachChunk) {
                isReady = false;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
            this.isCompleted = true;
        }
    }

    private static final class ClosingInputStream extends InputStream {

        private final InputStream delegate;
        private boolean isClosed = false;
        private boolean failOnRead = false;
        private boolean isReading = false;
        private boolean wasClosedDuringRead = false;

        /** Runs inside {@link #read(byte[], int, int)}, the way a directExecutor would deliver a callback. */
        private Runnable onRead;

        private ClosingInputStream(byte[] content) {
            this.delegate = new ByteArrayInputStream(content);
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (failOnRead) {
                throw new IOException("cannot read");
            }
            isReading = true;
            try {
                if (onRead != null) {
                    onRead.run();
                }
                return delegate.read(buffer, offset, length);
            } finally {
                isReading = false;
            }
        }

        @Override
        public void close() throws IOException {
            if (isReading) {
                wasClosedDuringRead = true;
            }
            isClosed = true;
            delegate.close();
        }
    }
}
