package io.kestra.core.exceptions;

/**
 * General exception that can be thrown when an AI service replies with an error.
 * When propagated in the context of a REST API call, this exception should
 * result in an HTTP 422 UNPROCESSABLE_ENTITY response.
 */
public class AiException extends KestraRuntimeException {

    /**
     * Creates a new {@link AiException} instance.
     */
    public AiException() {
        super();
    }

    /**
     * Creates a new {@link AiException} instance.
     *
     * @param aiErrorMessage the AI error message.
     */
    public AiException(final String aiErrorMessage) {
        super(aiErrorMessage);
    }
}
