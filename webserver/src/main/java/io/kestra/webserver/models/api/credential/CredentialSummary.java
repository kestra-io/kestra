package io.kestra.webserver.models.api.credential;

import java.time.Instant;

import io.kestra.fethr.credential.Credential;
import io.kestra.fethr.credential.CredentialType;

/**
 * A credential as the list shows it: who it is, not what it holds.
 *
 * <p>
 * Deliberately carries no material. Listing is a broad read, and a credential's secret half is
 * served only by asking for that one credential by name.
 */
public record CredentialSummary(
    String tenantId,
    String namespace,
    String name,
    String description,
    CredentialType type,
    Instant updated
) {
    public static CredentialSummary of(Credential credential) {
        return new CredentialSummary(
            credential.getTenantId(),
            credential.getNamespace(),
            credential.getName(),
            credential.getDescription(),
            credential.getType(),
            credential.getUpdated()
        );
    }
}
