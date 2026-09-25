package io.kestra.core.storages;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import io.kestra.core.models.annotations.Beta;

import jakarta.annotation.Nullable;

/**
 * Optional {@link StorageInterface} capability for generating a time-limited URL that lets a
 * caller (e.g. a remote task runner) access a storage object directly, without proxying the
 * transfer through the worker.
 */
@Beta
public interface SignedUrlCapable {
    enum Operation {
        GET,
        PUT
    }

    /**
     * Generates a signed URL for the given operation.
     *
     * @return the signed URL, or empty if this storage cannot or is configured not to sign URLs
     * @throws IOException if signing was attempted but failed
     */
    Optional<SignedUrl> sign(String tenantId, @Nullable String namespace, URI uri, Operation operation, Duration ttl) throws IOException;
}
