package io.kestra.core.exceptions;

/**
 * Stable machine-readable codes exposed in API error responses so that clients can branch on the
 * error type instead of parsing free-text messages.
 *
 * <p>Codes are part of the public API contract: renaming or removing a value is a breaking change,
 * adding a new value is not — clients must treat unrecognized codes as their closest
 * status-level equivalent.
 */
public enum ErrorCode {
    INTERNAL_ERROR,
    INVALID_REQUEST,
    INVALID_ENTITY,
    INVALID_QUERY_FILTERS,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    FLOW_NOT_FOUND,
    TENANT_NOT_FOUND,
    CONFLICT,
    LOCKED,
    RESOURCE_EXPIRED;

    /**
     * Resolves the default {@link ErrorCode} for an HTTP status code, used when the thrown
     * exception does not carry a more specific code.
     *
     * @param statusCode the HTTP status code.
     * @return the default {@link ErrorCode} for the given status.
     */
    public static ErrorCode fromHttpStatusCode(final int statusCode) {
        return switch (statusCode) {
            case 400 -> INVALID_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 409 -> CONFLICT;
            case 410 -> RESOURCE_EXPIRED;
            case 422 -> INVALID_ENTITY;
            case 423 -> LOCKED;
            default -> statusCode >= 400 && statusCode < 500 ? INVALID_REQUEST : INTERNAL_ERROR;
        };
    }
}
