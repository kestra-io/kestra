package io.kestra.webserver.models.api;

/**
 * Response body returned by batch mutating endpoints that accept the request and schedule
 * processing asynchronously. The caller uses {@code operationId} to correlate logs and
 * progress indicators; {@code totalItems} is the count of domain events submitted.
 */
public record ApiAsyncOperationResponse(String operationId, int totalItems) {}
