package io.kestra.fethr.auth;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * A security operation failed: the identity provider is unconfigured, or a call to it did not
 * succeed.
 *
 * <p>
 * Distinct from a client error on purpose. Onboarding should not read "Keycloak is unreachable" as
 * "that email is already taken", so this is a server fault and surfaces as a 5xx.
 */
public class KestraSecurityException extends KestraRuntimeException {

    public KestraSecurityException(final String message) {
        super(message);
    }

    public KestraSecurityException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
