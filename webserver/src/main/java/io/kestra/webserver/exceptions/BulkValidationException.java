package io.kestra.webserver.exceptions;

import io.kestra.webserver.responses.BulkErrorResponse;
import lombok.Getter;

/**
 * Thrown by bulk action endpoints when one or more items fail validation.
 * Handled by {@link BulkValidationExceptionHandler}, which maps it to an HTTP 400
 * response carrying a {@link BulkErrorResponse} body.
 */
@Getter
public class BulkValidationException extends RuntimeException {
    private final BulkErrorResponse bulkErrorResponse;

    public BulkValidationException(BulkErrorResponse bulkErrorResponse) {
        super(bulkErrorResponse.getMessage());
        this.bulkErrorResponse = bulkErrorResponse;
    }
}
