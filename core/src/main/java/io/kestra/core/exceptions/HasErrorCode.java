package io.kestra.core.exceptions;

/**
 * Contract for exceptions that carry a stable machine-readable {@link ErrorCode} to be exposed in
 * API error responses.
 */
public interface HasErrorCode {

    /**
     * @return the stable machine-readable code identifying this error.
     */
    ErrorCode getErrorCode();
}
