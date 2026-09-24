package io.kestra.controller.grpc.services;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.protobuf.ByteString;

import io.kestra.controller.RequiresControllerServer;
import io.kestra.controller.grpc.BooleanResponse;
import io.kestra.controller.grpc.KVKeyRequest;
import io.kestra.controller.grpc.KVPutEntry;
import io.kestra.controller.grpc.KVPutRequest;
import io.kestra.controller.grpc.KVPutResponse;
import io.kestra.controller.grpc.KVStoreServiceGrpc;
import io.kestra.controller.grpc.NamespaceRequest;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.controller.grpc.StreamChunk;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.grpc.streaming.ChunkedStreamWriter;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.storages.kv.KVBackend;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;
import io.kestra.core.validations.validator.TenantIdValidator;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC service reading and writing KV entries on behalf of workers that do not reach the internal storage
 * holding them.
 * <p>
 * The entries are handled by the {@link KVBackend} of this server, so they land where every other server reads
 * them. The tenant, the namespace and the key become a storage path, so they are validated here rather than
 * trusted from the worker. A value is never held in memory beyond one chunk: a put larger than a chunk is
 * spooled to a temporary file before it is handed to the backend, and a read is streamed from it.
 */
@Slf4j
@Singleton
@RequiresControllerServer
public class GrpcKVStoreControllerService extends KVStoreServiceGrpc.KVStoreServiceImplBase implements WorkerControllerService {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]*");

    private final KVBackend backend;

    @Inject
    public GrpcKVStoreControllerService(KVBackend backend) {
        this.backend = backend;
    }

    /** {@inheritDoc} */
    @Override
    public StreamObserver<KVPutRequest> put(StreamObserver<KVPutResponse> responseObserver) {
        return new StreamObserver<>() {
            private KVPutRequest entryRequest;
            private KVPutEntry entry;
            private ByteString firstChunk;
            private Path spool;
            private OutputStream spoolOutput;
            private boolean failed;

            @Override
            public void onNext(KVPutRequest request) {
                if (failed) {
                    return;
                }

                try {
                    switch (request.getPayloadCase()) {
                        case ENTRY -> {
                            if (entry != null) {
                                throw new IllegalArgumentException("A KV put carries a single entry, but a second one was received.");
                            }
                            validate(request.getTenantId(), request.getEntry().getNamespace(), request.getEntry().getKey());
                            entryRequest = request;
                            entry = request.getEntry();
                        }
                        case CHUNK -> {
                            if (entry == null) {
                                throw new IllegalArgumentException("A KV put must start with its entry, but a value chunk was received first.");
                            }
                            append(request.getChunk().getContent());
                        }
                        default -> throw new IllegalArgumentException("A KV put message carries neither an entry nor a value chunk.");
                    }
                } catch (Exception e) {
                    fail(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.debug("A worker aborted a KV put", t);
                discardSpool();
            }

            @Override
            public void onCompleted() {
                if (failed) {
                    return;
                }

                try {
                    if (entry == null) {
                        throw new IllegalArgumentException("A KV put must carry an entry, but none was received.");
                    }
                    backend.put(entryRequest.getTenantId(), entry.getNamespace(), entry.getKey(), metadata(entry), value(), entry.getOverwrite());

                    responseObserver.onNext(KVPutResponse.newBuilder().setHeader(entryRequest.getHeader()).build());
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    fail(e);
                } finally {
                    discardSpool();
                }
            }

            // Most values fit in a single chunk, so the spool is only created once a second chunk arrives.
            private void append(ByteString chunk) throws IOException {
                if (spoolOutput == null && firstChunk == null) {
                    firstChunk = chunk;
                    return;
                }
                if (spoolOutput == null) {
                    spool = Files.createTempFile("kestra-kv-", ".part");
                    spoolOutput = new BufferedOutputStream(Files.newOutputStream(spool));
                    firstChunk.writeTo(spoolOutput);
                    firstChunk = null;
                }
                chunk.writeTo(spoolOutput);
            }

            private InputStream value() throws IOException {
                if (spoolOutput == null) {
                    return (firstChunk == null ? ByteString.EMPTY : firstChunk).newInput();
                }
                spoolOutput.close();
                return Files.newInputStream(spool);
            }

            private void fail(Exception e) {
                failed = true;
                discardSpool();
                responseObserver.onError(toStatus(e));
            }

            private void discardSpool() {
                try {
                    if (spoolOutput != null) {
                        spoolOutput.close();
                    }
                    if (spool != null) {
                        Files.deleteIfExists(spool);
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete the spooled KV value '{}'", spool, e);
                }
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public void getRawValue(KVKeyRequest request, StreamObserver<StreamChunk> responseObserver) {
        final Optional<InputStream> raw;
        try {
            validate(request);
            raw = backend.getRawValue(request.getTenantId(), request.getNamespace(), request.getKey());
        } catch (Exception e) {
            responseObserver.onError(toStatus(e));
            return;
        }

        if (raw.isEmpty()) {
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("No value is stored for the key '%s' of namespace '%s'.".formatted(request.getKey(), request.getNamespace()))
                    .asRuntimeException()
            );
            return;
        }

        ChunkedStreamWriter.write(raw.get(), responseObserver, request.getHeader());
    }

    /** {@inheritDoc} */
    @Override
    public void get(KVKeyRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            validate(request);
            respond(request.getHeader(), responseObserver, backend.get(request.getTenantId(), request.getNamespace(), request.getKey()).orElse(null));
        } catch (Exception e) {
            responseObserver.onError(toStatus(e));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void list(NamespaceRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            validateStorageLocation(request.getTenantId(), request.getNamespace());
            respond(request.getHeader(), responseObserver, backend.list(request.getTenantId(), request.getNamespace()));
        } catch (Exception e) {
            responseObserver.onError(toStatus(e));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void delete(KVKeyRequest request, StreamObserver<BooleanResponse> responseObserver) {
        try {
            validate(request);
            boolean deleted = backend.delete(request.getTenantId(), request.getNamespace(), request.getKey());

            responseObserver.onNext(BooleanResponse.newBuilder().setHeader(request.getHeader()).setValue(deleted).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(toStatus(e));
        }
    }

    private static void validate(KVKeyRequest request) {
        validate(request.getTenantId(), request.getNamespace(), request.getKey());
    }

    private static void validate(String tenantId, String namespace, String key) {
        log.trace("Received KV request: tenantId={}, namespace={}, key={}", tenantId, namespace, key);

        validateStorageLocation(tenantId, namespace);
        KVStore.validateKey(key);
    }

    private static void validateStorageLocation(String tenantId, String namespace) {
        if (!TenantIdValidator.isValid(tenantId)) {
            throw new IllegalArgumentException("'%s' is not a valid tenant.".formatted(tenantId));
        }
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("'%s' is not a valid namespace.".formatted(namespace));
        }
    }

    private static void respond(RequestOrResponseHeader header, StreamObserver<OpaqueData> responseObserver, Object payload) {
        MessageFormat messageFormat = MessageFormat.resolve(header.getMessageFormat());

        responseObserver.onNext(OpaqueData.newBuilder().setHeader(header).setMessage(messageFormat.toByteString(payload)).build());
        responseObserver.onCompleted();
    }

    private static KVMetadata metadata(KVPutEntry entry) {
        if (!entry.hasDescription() && !entry.hasExpirationDate()) {
            return null;
        }
        return new KVMetadata(
            entry.hasDescription() ? entry.getDescription() : null,
            entry.hasExpirationDate() ? Instant.parse(entry.getExpirationDate()) : null
        );
    }

    private static StatusRuntimeException toStatus(Exception e) {
        Status status = switch (e) {
            case StatusRuntimeException statusException -> statusException.getStatus();
            case ResourceExpiredException ignored -> Status.FAILED_PRECONDITION;
            case KVStoreException ignored -> Status.ALREADY_EXISTS;
            case IllegalArgumentException ignored -> Status.INVALID_ARGUMENT;
            default -> {
                log.error("Error while accessing the KV store on behalf of a worker", e);
                yield Status.INTERNAL;
            }
        };
        return status.getDescription() == null ? status.withDescription(e.getMessage()).asRuntimeException() : status.asRuntimeException();
    }
}
