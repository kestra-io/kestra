package io.kestra.core.storages;

import java.io.IOException;
import java.util.Optional;

import io.kestra.core.annotations.Retryable;

/**
 * Capability of a {@link StorageInterface} to presign requests reading or writing a single object, so that a client
 * holding no credentials for the backend can send them over plain HTTPS until they expire.
 * <p>
 * A storage implements it in addition to {@link StorageInterface}. An object is resolved as
 * {@link StorageInterface#get} and {@link StorageInterface#put} resolve it, including
 * {@link StorageInterface#parentTraversalGuard}. A {@link PresignedRequest.Method#PUT} must store the request's metadata
 * and leave the object listable as {@link StorageInterface#put} would.
 */
public interface PresignableStorage {

    /**
     * @return the presigned request, or empty when the backend holding this object cannot presign requests, as
     *         happens behind a decorator dispatching to several backends.
     * @throws IOException if the backend fails to presign the request.
     */
    @Retryable(includes = { IOException.class })
    Optional<PresignedRequest> presign(PresignRequest request) throws IOException;

    /**
     * Presigns through {@code storage} when it is presignable, as found by {@link StorageInterface#as}.
     *
     * @return the presigned request, or empty when {@code storage} cannot presign requests.
     */
    static Optional<PresignedRequest> presign(StorageInterface storage, PresignRequest request) throws IOException {
        Optional<PresignableStorage> presignable = storage.as(PresignableStorage.class);
        if (presignable.isEmpty()) {
            return Optional.empty();
        }
        return presignable.get().presign(request);
    }
}
