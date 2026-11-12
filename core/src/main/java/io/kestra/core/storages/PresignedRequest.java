package io.kestra.core.storages;

import java.net.URI;
import java.util.Map;

import lombok.Builder;

/**
 * An HTTP request against the backend of a {@link StorageInterface}, authorized by a signature carried in its URL, so
 * that whoever sends it needs no credentials for that backend. A storage builds it with {@link #builder()}, so that a
 * field added later does not break it.
 *
 * @param method the HTTP method the signature is valid for.
 * @param url the presigned URL, a bearer capability until it expires.
 * @param headers the headers the signature covers, which must be sent with the request exactly as given.
 */
@Builder
public record PresignedRequest(Method method, URI url, Map<String, String> headers) {

    public PresignedRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public enum Method {
        GET,
        PUT
    }
}
