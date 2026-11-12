package io.kestra.core.storages;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import jakarta.annotation.Nullable;
import lombok.Builder;

/**
 * A request to {@link PresignableStorage#presign}, built with {@link #builder()} so that a field added later does not
 * break the storages implementing it.
 *
 * @param uri the object, resolved as {@link StorageInterface#get} and {@link StorageInterface#put} resolve it.
 * @param metadata the metadata to store with the object, for a {@link PresignedRequest.Method#PUT}.
 * @param ttl how long the request stays valid; a backend may cap it lower.
 * @param contentLength the length of the body a {@link PresignedRequest.Method#PUT} sends, or {@code null} when unknown.
 *        A backend may sign it, or refuse a length above what a single request can carry.
 */
@Builder
public record PresignRequest(
    String tenantId,
    @Nullable String namespace,
    URI uri,
    PresignedRequest.Method method,
    Map<String, String> metadata,
    Duration ttl,
    @Nullable Long contentLength) {

    public PresignRequest {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
