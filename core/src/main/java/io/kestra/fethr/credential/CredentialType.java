package io.kestra.fethr.credential;

import lombok.Getter;
import lombok.ToString;

/**
 * The shapes a stored credential can take. Each value maps to one {@link Credential} subtype
 * through the Jackson subtype registry on that class, and is the discriminator persisted with the
 * row -- so values are never renamed or removed without a data migration.
 */
@Getter
@ToString
public enum CredentialType {
    API_KEY("API Key"),
    OAUTH2("OAuth2 Client Credentials"),
    BEARER_TOKEN("Bearer Token"),
    BASIC_AUTH("Basic Auth (username + password)"),
    CERNER_FHIR("Cerner FHIR (SMART Backend Services)");

    private final String label;

    CredentialType(String label) {
        this.label = label;
    }
}
