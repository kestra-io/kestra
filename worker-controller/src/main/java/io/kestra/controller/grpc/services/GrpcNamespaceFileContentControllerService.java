package io.kestra.controller.grpc.services;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import io.kestra.controller.RequiresControllerServer;
import io.kestra.controller.grpc.BooleanResponse;
import io.kestra.controller.grpc.NamespaceFileContentServiceGrpc;
import io.kestra.controller.grpc.NamespaceFileObject;
import io.kestra.controller.grpc.NamespaceFileObjectRequest;
import io.kestra.controller.grpc.NamespaceFilePutRequest;
import io.kestra.controller.grpc.NamespaceFilePutResponse;
import io.kestra.controller.grpc.StreamChunk;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.grpc.streaming.ChunkSpool;
import io.kestra.controller.grpc.streaming.ChunkedStreamWriter;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.NamespaceFileBackend;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC service reading and writing the content of namespace files on behalf of workers that do not reach the
 * internal storage holding it.
 * <p>
 * A worker addresses an object by its namespace and its path under the namespace files root, never by a storage URI,
 * and both are validated here since they become a storage path.
 */
@Slf4j
@Singleton
@RequiresControllerServer
public class GrpcNamespaceFileContentControllerService extends NamespaceFileContentServiceGrpc.NamespaceFileContentServiceImplBase
    implements WorkerControllerService {

    private final NamespaceFileBackend backend;

    @Inject
    public GrpcNamespaceFileContentControllerService(NamespaceFileBackend backend) {
        this.backend = backend;
    }

    @Override
    public void get(NamespaceFileObjectRequest request, StreamObserver<StreamChunk> responseObserver) {
        final InputStream content;
        try {
            content = backend.get(request.getTenantId(), file(request));
        } catch (FileNotFoundException e) {
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription("No content is stored for '%s' in namespace '%s'.".formatted(request.getObject(), request.getNamespace()))
                    .asRuntimeException()
            );
            return;
        } catch (Exception e) {
            responseObserver.onError(toStatus(e));
            return;
        }

        ChunkedStreamWriter.write(content, responseObserver, request.getHeader());
    }

    @Override
    public void exists(NamespaceFileObjectRequest request, StreamObserver<BooleanResponse> responseObserver) {
        respond(request, responseObserver, file -> backend.exists(request.getTenantId(), file));
    }

    @Override
    public void createDirectory(NamespaceFileObjectRequest request, StreamObserver<BooleanResponse> responseObserver) {
        respond(request, responseObserver, directory -> {
            backend.createDirectory(request.getTenantId(), directory);
            return true;
        });
    }

    @Override
    public void delete(NamespaceFileObjectRequest request, StreamObserver<BooleanResponse> responseObserver) {
        respond(request, responseObserver, file -> backend.delete(request.getTenantId(), file));
    }

    @Override
    public StreamObserver<NamespaceFilePutRequest> put(StreamObserver<NamespaceFilePutResponse> responseObserver) {
        return new StreamObserver<>() {
            private final ChunkSpool spool = new ChunkSpool("kestra-namespace-file-");
            private NamespaceFilePutRequest objectRequest;
            private NamespaceFile file;
            private boolean failed;

            @Override
            public void onNext(NamespaceFilePutRequest request) {
                if (failed) {
                    return;
                }

                try {
                    switch (request.getPayloadCase()) {
                        case OBJECT -> {
                            if (file != null) {
                                throw new IllegalArgumentException("A namespace file put carries a single object, but a second one was received.");
                            }
                            NamespaceFileObject object = request.getObject();
                            file = file(request.getTenantId(), object.getNamespace(), object.getObject());
                            objectRequest = request;
                        }
                        case CHUNK -> {
                            if (file == null) {
                                throw new IllegalArgumentException("A namespace file put must start with its object, but a content chunk was received first.");
                            }
                            spool.append(request.getChunk().getContent());
                        }
                        default -> throw new IllegalArgumentException("A namespace file put message carries neither an object nor a content chunk.");
                    }
                } catch (Exception e) {
                    fail(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.debug("A worker aborted a namespace file put", t);
                spool.close();
            }

            @Override
            public void onCompleted() {
                if (failed) {
                    return;
                }

                try {
                    if (file == null) {
                        throw new IllegalArgumentException("A namespace file put must carry an object, but none was received.");
                    }
                    long size = backend.put(objectRequest.getTenantId(), file, spool.content());

                    responseObserver.onNext(NamespaceFilePutResponse.newBuilder().setHeader(objectRequest.getHeader()).setSize(size).build());
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    fail(e);
                } finally {
                    spool.close();
                }
            }

            private void fail(Exception e) {
                failed = true;
                spool.close();
                responseObserver.onError(toStatus(e));
            }
        };
    }

    private void respond(
        NamespaceFileObjectRequest request,
        StreamObserver<BooleanResponse> responseObserver,
        Operation operation) {
        try {
            boolean value = operation.apply(file(request));

            responseObserver.onNext(BooleanResponse.newBuilder().setHeader(request.getHeader()).setValue(value).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(toStatus(e));
        }
    }

    private static NamespaceFile file(NamespaceFileObjectRequest request) {
        return file(request.getTenantId(), request.getNamespace(), request.getObject());
    }

    private static NamespaceFile file(String tenantId, String namespace, String object) {
        StorageLocations.validate(tenantId, namespace);
        String normalized = NamespaceFile.normalize(Path.of(object)).toString();
        boolean directory = NamespaceFile.isDirectory(object) && !NamespaceFile.isDirectory(normalized);
        return NamespaceFile.of(namespace, directory ? normalized + "/" : normalized, 1);
    }

    private static StatusRuntimeException toStatus(Exception e) {
        Status status = switch (e) {
            case StatusRuntimeException statusException -> statusException.getStatus();
            case IllegalArgumentException ignored -> Status.INVALID_ARGUMENT;
            default -> {
                log.error("Error while accessing namespace file content on behalf of a worker", e);
                yield Status.INTERNAL;
            }
        };
        return status.getDescription() == null ? status.withDescription(e.getMessage()).asRuntimeException() : status.asRuntimeException();
    }

    private interface Operation {
        boolean apply(NamespaceFile file) throws IOException;
    }
}
