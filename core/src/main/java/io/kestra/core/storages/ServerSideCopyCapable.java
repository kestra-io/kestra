package io.kestra.core.storages;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import io.kestra.core.models.annotations.Beta;

import jakarta.annotation.Nullable;

/**
 * Optional {@link StorageInterface} capability for copying an object directly on the storage
 * backend (e.g. an S3 server-side copy), without downloading and re-uploading it through the worker.
 */
@Beta
public interface ServerSideCopyCapable {
    /**
     * Copies {@code source} to {@code target} server-side.
     *
     * @return the URI of the copied object, or empty if this storage cannot perform the copy (e.g. the
     *         source is not on the same backend, or the failure is one the caller should recover from by
     *         streaming the copy itself instead)
     * @throws IOException if the copy was attempted but failed for another reason
     */
    Optional<URI> copyFrom(String tenantId, @Nullable String namespace, URI target, URI source) throws IOException;
}
