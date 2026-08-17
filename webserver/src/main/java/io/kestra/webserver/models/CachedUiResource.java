package io.kestra.webserver.models;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;

/**
 * A static UI resource held fully in memory: raw bytes, optional precompressed variants and a strong entity tag,
 * so serving it never touches the jar and never compresses on the hot path.
 *
 * @param mediaType the content type of the resource.
 * @param raw       the identity (uncompressed) bytes.
 * @param gzip      the gzip-compressed bytes, or null when compression does not pay off for this resource.
 * @param brotli    the brotli-compressed bytes, or null when unavailable or not worthwhile.
 * @param etagBase  hex digest of the raw bytes; variant entity tags are derived from it per content coding.
 */
public record CachedUiResource(
    MediaType mediaType,
    byte[] raw,
    @Nullable byte[] gzip,
    @Nullable byte[] brotli,
    String etagBase
) {
    /**
     * @return the approximate heap footprint of this entry, used to bound the UI resource cache.
     */
    public long weight() {
        return raw.length
            + (gzip == null ? 0 : gzip.length)
            + (brotli == null ? 0 : brotli.length)
            + etagBase.length();
    }
}
