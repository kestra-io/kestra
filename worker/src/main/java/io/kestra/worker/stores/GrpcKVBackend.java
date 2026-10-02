package io.kestra.worker.stores;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.ByteString;

import io.kestra.controller.grpc.KVKeyRequest;
import io.kestra.controller.grpc.KVPutEntry;
import io.kestra.controller.grpc.KVPutRequest;
import io.kestra.controller.grpc.KVPutResponse;
import io.kestra.controller.grpc.KVStoreServiceGrpc;
import io.kestra.controller.grpc.NamespaceRequest;
import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.controller.grpc.StreamChunk;
import io.kestra.controller.grpc.streaming.ChunkedStreamWriter;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.storages.kv.KVBackend;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStoreException;
import io.kestra.core.storages.kv.StorageKVBackend;
import io.kestra.core.worker.models.WorkerInfo;

import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A {@link KVBackend} reached through the controller, for a worker that does not reach the internal storage
 * holding the entries. Every operation of an entry goes through the controller, and values are streamed both
 * ways, so the worker never touches its own storage for the KV store.
 */
@Singleton
@Replaces(StorageKVBackend.class)
@Requires(condition = KVStoreFromControllerCondition.class)
public class GrpcKVBackend implements KVBackend {

    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;
    private static final TypeReference<List<KVEntry>> ENTRY_LIST = new TypeReference<>() {
    };

    private final KVStoreServiceGrpc.KVStoreServiceStub kvStoreStub;
    private final KVStoreServiceGrpc.KVStoreServiceBlockingStub kvStoreBlockingStub;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcKVBackend(
        KVStoreServiceGrpc.KVStoreServiceStub kvStoreStub,
        KVStoreServiceGrpc.KVStoreServiceBlockingStub kvStoreBlockingStub,
        WorkerInfo workerInfo) {
        this.kvStoreStub = kvStoreStub;
        this.kvStoreBlockingStub = kvStoreBlockingStub;
        this.workerInfo = workerInfo;
    }

    @Override
    public void put(String tenant, String namespace, String key, @Nullable KVMetadata metadata, InputStream value, boolean overwrite) throws IOException {
        PutCall call = new PutCall();
        kvStoreStub.put(call);

        try (value) {
            if (call.awaitReady()) {
                call.requests.onNext(putRequest(tenant).setEntry(entry(namespace, key, metadata, overwrite)).build());
            }
            byte[] buffer = new byte[ChunkedStreamWriter.DEFAULT_CHUNK_SIZE];
            int read;
            while (call.awaitReady() && (read = value.readNBytes(buffer, 0, buffer.length)) > 0) {
                call.requests.onNext(
                    putRequest(tenant)
                        .setChunk(StreamChunk.newBuilder().setHeader(header()).setContent(ByteString.copyFrom(buffer, 0, read)))
                        .build()
                );
            }
            if (!call.completion.isDone()) {
                call.requests.onCompleted();
            }
            call.completion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.requests.cancel("The worker was interrupted.", e);
            throw new InterruptedIOException("Interrupted while putting the key '%s' of namespace '%s' through the controller.".formatted(key, namespace));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof StatusRuntimeException statusException) {
                throw translate(statusException, namespace, key);
            }
            throw new IOException("Cannot put the key '%s' of namespace '%s' through the controller.".formatted(key, namespace), e.getCause());
        } catch (IOException | RuntimeException e) {
            call.requests.cancel("The worker failed to read the value to put.", e);
            throw e;
        }
    }

    @Override
    public Optional<InputStream> getRawValue(String tenant, String namespace, String key) throws ResourceExpiredException {
        // The call is bound to this context so that closing the stream before its end cancels it, rather than leaking it.
        Context.CancellableContext context = Context.current().withCancellation();
        Context previous = context.attach();
        try {
            Iterator<StreamChunk> chunks = kvStoreBlockingStub.getRawValue(keyRequest(tenant, namespace, key));
            // A missing or expired value is reported as the status of the call, which surfaces on the first read.
            chunks.hasNext();
            return Optional.of(new ChunkInputStream(chunks, context));
        } catch (StatusRuntimeException e) {
            context.cancel(null);
            switch (e.getStatus().getCode()) {
                case NOT_FOUND -> {
                    return Optional.empty();
                }
                case FAILED_PRECONDITION -> throw new ResourceExpiredException(e.getStatus().getDescription(), e);
                default -> throw translate(e, namespace, key);
            }
        } finally {
            context.detach(previous);
        }
    }

    @Override
    public Optional<KVEntry> get(String tenant, String namespace, String key) {
        KVKeyRequest request = keyRequest(tenant, namespace, key);
        return Optional.ofNullable(call(() -> MESSAGE_FORMAT.fromByteString(kvStoreBlockingStub.get(request).getMessage(), KVEntry.class), namespace, key));
    }

    @Override
    public List<KVEntry> list(String tenant, String namespace) {
        NamespaceRequest request = NamespaceRequest.newBuilder()
            .setHeader(header())
            .setTenantId(tenant)
            .setNamespace(namespace)
            .build();
        return call(() -> MESSAGE_FORMAT.fromByteString(kvStoreBlockingStub.list(request).getMessage(), ENTRY_LIST), namespace, null);
    }

    @Override
    public boolean delete(String tenant, String namespace, String key) {
        KVKeyRequest request = keyRequest(tenant, namespace, key);
        return call(() -> kvStoreBlockingStub.delete(request).getValue(), namespace, key);
    }

    private static <T> T call(Supplier<T> call, String namespace, @Nullable String key) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            throw translate(e, namespace, key);
        }
    }

    private static RuntimeException translate(StatusRuntimeException e, String namespace, @Nullable String key) {
        String description = e.getStatus().getDescription();
        return switch (e.getStatus().getCode()) {
            case ALREADY_EXISTS, PERMISSION_DENIED -> new KVStoreException(description, e);
            case INVALID_ARGUMENT -> new IllegalArgumentException(description, e);
            case UNIMPLEMENTED -> new KestraRuntimeException(
                ("Cannot access the KV store of namespace '%s' through the controller, which serves no KV store entries. "
                    + "Upgrade the controller, or set '%s' to STORAGE so this worker reads the KV store from internal storage.")
                    .formatted(namespace, KVWorkerAccess.CONFIG_KEY),
                e
            );
            default -> e;
        };
    }

    private KVPutRequest.Builder putRequest(String tenant) {
        return KVPutRequest.newBuilder()
            .setHeader(header())
            .setTenantId(tenant);
    }

    private static KVPutEntry entry(String namespace, String key, @Nullable KVMetadata metadata, boolean overwrite) {
        KVPutEntry.Builder entry = KVPutEntry.newBuilder()
            .setNamespace(namespace)
            .setKey(key)
            .setOverwrite(overwrite);
        if (metadata != null && metadata.getDescription() != null) {
            entry.setDescription(metadata.getDescription());
        }
        if (metadata != null && metadata.getExpirationDate() != null) {
            entry.setExpirationDate(metadata.getExpirationDate().toString());
        }
        return entry.build();
    }

    private KVKeyRequest keyRequest(String tenant, String namespace, String key) {
        return KVKeyRequest.newBuilder()
            .setHeader(header())
            .setTenantId(tenant)
            .setNamespace(namespace)
            .setKey(key)
            .build();
    }

    private RequestOrResponseHeader header() {
        return RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId());
    }

    /**
     * A client-streaming put that only sends while the transport is ready, so that a slow controller does not make
     * the worker buffer a second copy of the value, and that stops sending once the controller ended the call.
     */
    private static final class PutCall implements ClientResponseObserver<KVPutRequest, KVPutResponse> {
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final Object readiness = new Object();
        private ClientCallStreamObserver<KVPutRequest> requests;

        @Override
        public void beforeStart(ClientCallStreamObserver<KVPutRequest> requests) {
            this.requests = requests;
            requests.setOnReadyHandler(this::signal);
        }

        @Override
        public void onNext(KVPutResponse response) {
        }

        @Override
        public void onError(Throwable t) {
            completion.completeExceptionally(t);
            signal();
        }

        @Override
        public void onCompleted() {
            completion.complete(null);
            signal();
        }

        /**
         * @return {@code true} once the next message can be sent, {@code false} if the call already ended.
         */
        boolean awaitReady() throws InterruptedException {
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

    /**
     * The chunks of a value, read from the call as the stream is read. Closing it cancels the call.
     */
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
                throw new IOException("Cannot read a KV value through the controller.", e);
            }
        }
    }
}
