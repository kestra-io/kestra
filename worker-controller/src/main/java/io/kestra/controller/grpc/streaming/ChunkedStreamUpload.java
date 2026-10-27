package io.kestra.controller.grpc.streaming;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.google.protobuf.ByteString;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;

/**
 * Streams an {@link InputStream} to a gRPC server over a client-streaming call, as a leading message followed by
 * one message per chunk.
 * <p>
 * A message is only sent while the transport is ready, so that a slow server does not make the client buffer a
 * second copy of the payload, and sending stops once the server ended the call.
 */
public final class ChunkedStreamUpload {

    private ChunkedStreamUpload() {
    }

    /**
     * @param content the payload, read to its end and left open.
     * @param chunk wraps a chunk of the content into a message.
     * @throws StatusRuntimeException if the server failed the call.
     */
    public static <ReqT, RespT> RespT upload(
        Function<StreamObserver<RespT>, StreamObserver<ReqT>> call,
        ReqT first,
        InputStream content,
        Function<ByteString, ReqT> chunk) throws IOException {
        Call<ReqT, RespT> upload = new Call<>();
        call.apply(upload);

        try {
            if (upload.awaitReady()) {
                upload.requests.onNext(first);
            }
            byte[] buffer = new byte[ChunkedStreamWriter.DEFAULT_CHUNK_SIZE];
            int read;
            while (upload.awaitReady() && (read = content.readNBytes(buffer, 0, buffer.length)) > 0) {
                upload.requests.onNext(chunk.apply(ByteString.copyFrom(buffer, 0, read)));
            }
            if (!upload.completion.isDone()) {
                upload.requests.onCompleted();
            }
            return upload.completion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            upload.requests.cancel("The client was interrupted.", e);
            throw new InterruptedIOException("Interrupted while streaming a payload to the controller.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof StatusRuntimeException statusException) {
                throw statusException;
            }
            throw new IOException("Cannot stream a payload to the controller.", e.getCause());
        } catch (IOException | RuntimeException e) {
            upload.requests.cancel("The client failed to read the payload to stream.", e);
            throw e;
        }
    }

    private static final class Call<ReqT, RespT> implements ClientResponseObserver<ReqT, RespT> {
        private final CompletableFuture<RespT> completion = new CompletableFuture<>();
        private final Object readiness = new Object();
        private ClientCallStreamObserver<ReqT> requests;
        private RespT response;

        @Override
        public void beforeStart(ClientCallStreamObserver<ReqT> requests) {
            this.requests = requests;
            requests.setOnReadyHandler(this::signal);
        }

        @Override
        public void onNext(RespT response) {
            this.response = response;
        }

        @Override
        public void onError(Throwable t) {
            completion.completeExceptionally(t);
            signal();
        }

        @Override
        public void onCompleted() {
            completion.complete(response);
            signal();
        }

        /**
         * @return {@code true} once the next message can be sent, {@code false} if the call already ended.
         */
        private boolean awaitReady() throws InterruptedException {
            synchronized (readiness) {
                while (!requests.isReady() && !completion.isDone()) {
                    readiness.wait();
                }
            }
            return !completion.isDone();
        }

        private void signal() {
            synchronized (readiness) {
                readiness.notifyAll();
            }
        }
    }
}
