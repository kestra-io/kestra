package io.kestra.webserver.models.api.credential;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.fethr.credential.ApiKeyCredential;
import io.kestra.fethr.credential.BasicAuthCredential;
import io.kestra.fethr.credential.BearerTokenCredential;
import io.kestra.fethr.credential.CernerFhirCredential;
import io.kestra.fethr.credential.Credential;
import io.kestra.fethr.credential.CredentialType;
import io.kestra.fethr.credential.FhirVersion;
import io.kestra.fethr.credential.OAuth2Credential;
import io.kestra.fethr.vault.CryptographicValue;

/**
 * One credential in full, material included.
 *
 * <p>
 * This is the read that returns secrets, so it is reached only by naming a single credential -- the
 * list deliberately cannot. It exists because the edit form has to round-trip what it is editing.
 *
 * <p>
 * The fields are the union across every {@link CredentialType}, with {@code NON_NULL} hiding the
 * ones that do not apply. That keeps the wire format flat and per-type, matching what the 1.x fork
 * produced from a parallel hierarchy of six classes, without carrying six classes to do it. Which
 * fields are filled is decided once, in {@link #of}.
 *
 * <p>
 * Material is flattened to plain strings here rather than by teaching
 * {@link CryptographicValue} to serialize as one: that type's JSON shape is the <em>stored</em>
 * shape, and flattening it would leave every credential already in the database unreadable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CredentialDetail(
    String tenantId,
    String namespace,
    String name,
    String description,
    CredentialType type,
    Instant updated,

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
    Boolean sandbox,
    String fhirBaseUrl,
    String conformance) {

    public static CredentialDetail of(Credential credential) {
        Builder builder = new Builder(credential);

        return switch (credential) {
            case ApiKeyCredential c -> builder.key(content(c.getKey())).build();
            case BearerTokenCredential c -> builder.bearerToken(content(c.getBearerToken())).build();
            case BasicAuthCredential c -> builder
                .username(content(c.getUsername()))
                .password(content(c.getPassword()))
                .build();
            case OAuth2Credential c -> builder
                .tokenUrl(c.getTokenUrl())
                .tenantKey(content(c.getTenantKey()))
                .clientId(content(c.getClientId()))
                .clientSecret(content(c.getClientSecret()))
                .scopes(c.getScopes())
                .build();
            case CernerFhirCredential c -> builder
                .tokenUrl(c.getTokenUrl())
                .tenantKey(content(c.getTenantKey()))
                .clientId(content(c.getClientId()))
                .clientSecret(content(c.getClientSecret()))
                .scopes(c.getScopes())
                .fhirVersion(c.getFhirVersion())
                .sandbox(c.isSandbox())
                .fhirBaseUrl(c.getFhirBaseUrl())
                .conformance(c.getConformance())
                .build();
            default -> throw new IllegalArgumentException(
                "Unknown credential type '" + credential.getClass().getName() + "'"
            );
        };
    }

    /** Null-tolerant: a sandbox Cerner credential has no tenant key until one is derived. */
    private static String content(CryptographicValue value) {
        return value == null ? null : value.content();
    }

    /**
     * Assembles the union record without every branch of {@link #of} having to name all nineteen
     * components positionally.
     */
    private static final class Builder {
        private final Credential credential;
        private String key;
        private String bearerToken;
        private String username;
        private String password;
        private String tokenUrl;
        private String tenantKey;
        private String clientId;
        private String clientSecret;
        private List<String> scopes;
        private FhirVersion fhirVersion;
        private Boolean sandbox;
        private String fhirBaseUrl;
        private String conformance;

        private Builder(Credential credential) {
            this.credential = credential;
        }

        private Builder key(String v) {
            this.key = v;
            return this;
        }

        private Builder bearerToken(String v) {
            this.bearerToken = v;
            return this;
        }

        private Builder username(String v) {
            this.username = v;
            return this;
        }

        private Builder password(String v) {
            this.password = v;
            return this;
        }

        private Builder tokenUrl(String v) {
            this.tokenUrl = v;
            return this;
        }

        private Builder tenantKey(String v) {
            this.tenantKey = v;
            return this;
        }

        private Builder clientId(String v) {
            this.clientId = v;
            return this;
        }

        private Builder clientSecret(String v) {
            this.clientSecret = v;
            return this;
        }

        private Builder scopes(List<String> v) {
            this.scopes = v;
            return this;
        }

        private Builder fhirVersion(FhirVersion v) {
            this.fhirVersion = v;
            return this;
        }

        private Builder sandbox(Boolean v) {
            this.sandbox = v;
            return this;
        }

        private Builder fhirBaseUrl(String v) {
            this.fhirBaseUrl = v;
            return this;
        }

        private Builder conformance(String v) {
            this.conformance = v;
            return this;
        }

        private CredentialDetail build() {
            return new CredentialDetail(
                credential.getTenantId(),
                credential.getNamespace(),
                credential.getName(),
                credential.getDescription(),
                credential.getType(),
                credential.getUpdated(),
                key, bearerToken, username, password,
                tokenUrl, tenantKey, clientId, clientSecret, scopes,
                fhirVersion, sandbox, fhirBaseUrl, conformance
            );
        }
    }
}
