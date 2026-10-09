package io.kestra.worker.stores;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

import io.kestra.controller.grpc.NamespaceFileContentServiceGrpc;
import io.kestra.controller.grpc.NamespaceFileObject;
import io.kestra.controller.grpc.NamespaceFileObjectRequest;
import io.kestra.controller.grpc.NamespaceFilePutRequest;
import io.kestra.controller.grpc.NamespaceFilePutResponse;
import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.controller.grpc.StreamChunk;
import io.kestra.controller.grpc.streaming.ChunkedStreamReader;
import io.kestra.controller.grpc.streaming.ChunkedStreamUpload;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.NamespaceFileBackend;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageNamespaceFileBackend;
import io.kestra.core.worker.models.WorkerInfo;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A {@link NamespaceFileBackend} reached through the controller, for a worker that does not reach the internal
 * storage holding the namespace files. Content is streamed both ways, so the worker never touches its own storage
 * for namespace files.
 */
@Singleton
@Replaces(StorageNamespaceFileBackend.class)
@Requires(condition = NamespaceFilesFromControllerCondition.class)
public class GrpcNamespaceFileBackend implements NamespaceFileBackend {

    private final NamespaceFileContentServiceGrpc.NamespaceFileContentServiceStub stub;
    private final NamespaceFileContentServiceGrpc.NamespaceFileContentServiceBlockingStub blockingStub;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcNamespaceFileBackend(
        NamespaceFileContentServiceGrpc.NamespaceFileContentServiceStub stub,
        NamespaceFileContentServiceGrpc.NamespaceFileContentServiceBlockingStub blockingStub,
        WorkerInfo workerInfo) {
        this.stub = stub;
        this.blockingStub = blockingStub;
        this.workerInfo = workerInfo;
    }

    @Override
    public InputStream get(String tenant, NamespaceFile file) throws IOException {
        NamespaceFileObjectRequest request = request(tenant, file);
        try {
            return ChunkedStreamReader.open(() -> blockingStub.get(request));
        } catch (StatusRuntimeException e) {
            if (Status.Code.NOT_FOUND == e.getStatus().getCode()) {
                throw new FileNotFoundException(e.getStatus().getDescription());
            }
            throw translate(e, file);
        }
    }

    @Override
    public boolean exists(String tenant, NamespaceFile file) {
        NamespaceFileObjectRequest request = request(tenant, file);
        return call(() -> blockingStub.exists(request).getValue(), file);
    }

    @Override
    public long put(String tenant, NamespaceFile file, InputStream content) throws IOException {
        try (content) {
            return ChunkedStreamUpload.<NamespaceFilePutRequest, NamespaceFilePutResponse>upload(
                stub::put,
                putRequest(tenant).setObject(NamespaceFileObject.newBuilder().setNamespace(file.namespace()).setObject(object(file))).build(),
                content,
                chunk -> putRequest(tenant).setChunk(StreamChunk.newBuilder().setHeader(header()).setContent(chunk)).build()
            ).getSize();
        } catch (StatusRuntimeException e) {
            throw translate(e, file);
        }
    }

    @Override
    public void createDirectory(String tenant, NamespaceFile directory) {
        NamespaceFileObjectRequest request = request(tenant, directory);
        call(() -> blockingStub.createDirectory(request).getValue(), directory);
    }

    @Override
    public boolean delete(String tenant, NamespaceFile file) {
        NamespaceFileObjectRequest request = request(tenant, file);
        return call(() -> blockingStub.delete(request).getValue(), file);
    }

    private static <T> T call(Supplier<T> call, NamespaceFile file) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            throw translate(e, file);
        }
    }

    private static RuntimeException translate(StatusRuntimeException e, NamespaceFile file) {
        return switch (e.getStatus().getCode()) {
            case INVALID_ARGUMENT -> new IllegalArgumentException(e.getStatus().getDescription(), e);
            case PERMISSION_DENIED -> new KestraRuntimeException(e.getStatus().getDescription(), e);
            case UNIMPLEMENTED -> new KestraRuntimeException(
                ("Cannot access the namespace files of namespace '%s' through the controller, which serves no namespace file content. "
                    + "Upgrade the controller, or set '%s' to STORAGE so this worker reads namespace files from internal storage.")
                    .formatted(file.namespace(), NamespaceFilesFromControllerCondition.CONFIG_KEY),
                e
            );
            default -> e;
        };
    }

    private NamespaceFileObjectRequest request(String tenant, NamespaceFile file) {
        return NamespaceFileObjectRequest.newBuilder()
            .setHeader(header())
            .setTenantId(tenant)
            .setNamespace(file.namespace())
            .setObject(object(file))
            .build();
    }

    private NamespaceFilePutRequest.Builder putRequest(String tenant) {
        return NamespaceFilePutRequest.newBuilder()
            .setHeader(header())
            .setTenantId(tenant);
    }

    private static String object(NamespaceFile file) {
        return StorageContext.logicalPath(file.uri()).substring(StorageContext.namespaceFilePrefix(file.namespace()).length() + 1);
    }

    private RequestOrResponseHeader header() {
        return RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId());
    }
}
