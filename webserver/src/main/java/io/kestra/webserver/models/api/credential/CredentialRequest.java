package io.kestra.webserver.models.api.credential;

import java.util.List;

import io.kestra.fethr.credential.ApiKeyCredential;
import io.kestra.fethr.credential.BasicAuthCredential;
import io.kestra.fethr.credential.BearerTokenCredential;
import io.kestra.fethr.credential.CernerFhirCredential;
import io.kestra.fethr.credential.Credential;
import io.kestra.fethr.credential.CredentialType;
import io.kestra.fethr.credential.FhirVersion;
import io.kestra.fethr.credential.OAuth2Credential;
import io.kestra.fethr.vault.CryptographicValue;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A credential as the API accepts it: material in plain strings.
 *
 * <p>
 * The entity cannot be bound straight from a request body. Its material is typed
 * {@link CryptographicValue}, which deserializes by <em>decrypting</em> -- correct when loading a
 * stored row, nonsense when applied to what a user just typed. So the wire type is separate, and
 * {@link #toEntity} is the one place plaintext becomes a {@link CryptographicValue}.
 *
 * <p>
 * Like {@link CredentialDetail}, the fields are the union across every {@link CredentialType} and
 * only the ones belonging to {@code type} are read. That mirrors the response shape, so the edit
 * form sends back what it was given.
 */
@Introspected
public record CredentialRequest(
    @NotBlank String name,
    String description,
    @NotNull CredentialType type,

    // API_KEY
    String key,

    // BEARER_TOKEN
    String bearerToken,

    // BASIC_AUTH
    String username,
    String password,

    // OAUTH2 and CERNER_FHIR
    String tokenUrl,
    String tenantKey,
    String clientId,
    String clientSecret,
    List<String> scopes,

    // CERNER_FHIR
    FhirVersion fhirVersion,
    Boolean sandbox) {

    /**
     * Builds the entity this request describes, wrapping each secret as it goes.
     *
     * @throws IllegalArgumentException if a field the type requires is absent, rather than storing
     *         a credential that cannot authenticate
     */
    public Credential toEntity(String tenantId, String namespace) {
        return switch (type) {
            case API_KEY -> ApiKeyCredential.builder()
                .key(required(key, "key"))
                .build();
            case BEARER_TOKEN -> BearerTokenCredential.builder()
                .bearerToken(required(bearerToken, "bearerToken"))
                .build();
            case BASIC_AUTH -> BasicAuthCredential.builder()
                .username(required(username, "username"))
                .password(required(password, "password"))
                .build();
            case OAUTH2 -> OAuth2Credential.builder()
                .tokenUrl(tokenUrl)
                .tenantKey(required(tenantKey, "tenantKey"))
                .clientId(required(clientId, "clientId"))
                .clientSecret(required(clientSecret, "clientSecret"))
                .scopes(scopes)
                .build();
            case CERNER_FHIR -> CernerFhirCredential.builder()
                // A sandbox credential has its tenant filled in server-side, so none is required.
                .tenantKey(Boolean.TRUE.equals(sandbox) ? optional(tenantKey) : required(tenantKey, "tenantKey"))
                .clientId(required(clientId, "clientId"))
                .clientSecret(required(clientSecret, "clientSecret"))
                .scopes(scopes)
                .fhirVersion(fhirVersion)
                .sandbox(Boolean.TRUE.equals(sandbox))
                .build();
        };
    }

    private static CryptographicValue required(String plaintext, String field) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("A credential of this type requires '" + field + "'.");
        }
        return new CryptographicValue(plaintext);
    }

    private static CryptographicValue optional(String plaintext) {
        return plaintext == null || plaintext.isBlank() ? null : new CryptographicValue(plaintext);
    }
}
