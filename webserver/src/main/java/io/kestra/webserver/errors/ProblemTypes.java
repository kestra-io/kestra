package io.kestra.webserver.errors;

/**
 * The closed Open Source catalog of {@link ProblemType}s.
 *
 * <p>Adding a constant here is the review gate for a new error kind: each one is a permanent, published URI
 * that must also gain its documentation page under {@link ProblemType#BASE_URI} in the same change, so a
 * client following the {@code type} it is given is not sent to a 404. Prefer reusing an existing type over
 * introducing one that no client would branch on differently.
 */
public final class ProblemTypes {
    // Malformed or unusable request payload.
    public static final ProblemType INVALID_JSON = ProblemType.of("invalid-json", "Invalid JSON", 422);
    public static final ProblemType INVALID_PLUGIN_TYPE = ProblemType.of("invalid-plugin-type", "Invalid plugin type", 422);
    public static final ProblemType INVALID_FORMAT = ProblemType.of("invalid-format", "Invalid value format", 422);
    public static final ProblemType INVALID_REQUEST_BODY = ProblemType.of("invalid-request-body", "Invalid request body", 422);
    public static final ProblemType INVALID_ARGUMENT = ProblemType.of("invalid-argument", "Invalid argument", 422);

    // Malformed request line or query string.
    public static final ProblemType BAD_REQUEST = ProblemType.of("bad-request", "Bad request", 400);
    public static final ProblemType INVALID_QUERY_PARAMETER = ProblemType.of("invalid-query-parameter", "Invalid query parameter", 400);
    public static final ProblemType INVALID_QUERY_FILTERS = ProblemType.of("invalid-query-filters", "Invalid query filters", 400);

    // Well-formed request carrying an entity that fails validation.
    public static final ProblemType VALIDATION_FAILED = ProblemType.of("validation-failed", "Validation failed", 422);
    public static final ProblemType INVALID_ENTITY = ProblemType.of("invalid-entity", "Invalid entity", 422);
    public static final ProblemType BULK_VALIDATION_FAILED = ProblemType.of("bulk-validation-failed", "Bulk validation failed", 400);

    // Authentication and authorization.
    public static final ProblemType UNAUTHENTICATED = ProblemType.of("unauthenticated", "Authentication required", 401);
    public static final ProblemType FORBIDDEN = ProblemType.of("forbidden", "Access denied", 403);

    // Resource state.
    public static final ProblemType NOT_FOUND = ProblemType.of("not-found", "Resource not found", 404);
    public static final ProblemType CONFLICT = ProblemType.of("conflict", "Conflict", 409);
    public static final ProblemType ENTITY_ALREADY_EXISTS = ProblemType.of("entity-already-exists", "Entity already exists", 409);
    public static final ProblemType RESOURCE_EXPIRED = ProblemType.of("resource-expired", "Resource has expired", 410);
    public static final ProblemType LOCKED = ProblemType.of("locked", "Resource is locked", 423);

    // Protocol-level rejections, raised by the framework rather than by Kestra.
    public static final ProblemType METHOD_NOT_ALLOWED = ProblemType.of("method-not-allowed", "Method not allowed", 405);
    public static final ProblemType NOT_ACCEPTABLE = ProblemType.of("not-acceptable", "Not acceptable", 406);
    public static final ProblemType PAYLOAD_TOO_LARGE = ProblemType.of("payload-too-large", "Payload too large", 413);
    public static final ProblemType UNSUPPORTED_MEDIA_TYPE = ProblemType.of("unsupported-media-type", "Unsupported media type", 415);
    public static final ProblemType TOO_MANY_REQUESTS = ProblemType.of("too-many-requests", "Too many requests", 429);

    // AI Copilot.
    public static final ProblemType AI_REQUEST_FAILED = ProblemType.of("ai-request-failed", "AI request failed", 422);

    // Server errors. Their detail is always a fixed, non-revealing string; see ProblemFactory.
    public static final ProblemType INTERNAL_ERROR = ProblemType.of("internal-error", "Internal server error", 500);
    public static final ProblemType MIGRATION_REQUIRED = ProblemType.of("migration-required", "Migration required", 503);
    public static final ProblemType SERVICE_UNAVAILABLE = ProblemType.of("service-unavailable", "Service unavailable", 503);
    public static final ProblemType TIMEOUT = ProblemType.of("timeout", "Operation timed out", 504);


    /**
     * The type to report when only an HTTP status is known — a status passed through from
     * {@code HttpStatusException}, or a response that reached the pipeline without a body.
     *
     * <p>Unrecognised statuses collapse to the generic type for their class, so an unmapped 4xx is never
     * reported as a server error.
     */
    public static ProblemType byStatus(final int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 410 -> RESOURCE_EXPIRED;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> INVALID_ENTITY;
            case 423 -> LOCKED;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> SERVICE_UNAVAILABLE;
            case 504 -> TIMEOUT;
            default -> 500 > status ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
