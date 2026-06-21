package io.kestra.webserver.models.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body returned by batch mutating endpoints that accept the request and schedule
 * processing asynchronously. The caller uses {@code operationId} to correlate logs and
 * progress indicators; {@code totalItems} is the count of domain events submitted.
 */
public record ApiAsyncOperationResponse(
    @Schema(description = "The operation identifier used to correlate logs and progress indicators") String operationId,
    @Schema(description = "The number of domain events submitted for asynchronous processing") int totalItems
) {}
