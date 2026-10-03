package io.kestra.core.storages;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import io.kestra.core.models.annotations.Beta;

/**
 * A time-limited, direct-access URL to a storage object, returned by {@link SignedUrlCapable}.
 *
 * @param url the signed URL
 * @param headers additional headers the caller must send with the request (e.g. content headers for a PUT)
 * @param expiresAt when the URL stops being valid; may be earlier than requested if the storage backend capped the TTL
 */
@Beta
public record SignedUrl(URI url, Map<String, String> headers, Instant expiresAt) {
    /**
     * Redacted so that logging or dumping this object can't leak the URL or its headers.
     */
    @Override
    public String toString() {
        return "SignedUrl[url=<redacted>, headers=<redacted, %d entries>, expiresAt=%s]"
            .formatted(headers == null ? 0 : headers.size(), expiresAt);
    }
}
