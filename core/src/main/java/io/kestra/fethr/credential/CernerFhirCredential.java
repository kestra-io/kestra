package io.kestra.fethr.credential;

import java.util.List;

import io.kestra.fethr.vault.CryptographicValue;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Cerner (Oracle Health) FHIR credential for SMART Backend Services.
 *
 * <p>
 * The user supplies the FHIR version, the OAuth2 client id and secret, the scopes to request, and
 * either a vendor tenant id or the {@link #sandbox} flag. {@link #tokenUrl}, {@link #fhirBaseUrl}
 * and {@link #conformance} are not user input: they are derived at save time by composing the base
 * URL and fetching the tenant's conformance statement, which both proves the tenant resolves to a
 * live FHIR server and yields the token endpoint to persist.
 *
 * <p>
 * <strong>That derivation is not yet ported.</strong> It lives in the FHIR connector
 * ({@code CernerCredentialDiscovery}, {@code CernerCredentialLoginVerifier}), which is a later
 * wave. The type is here so credentials of this type already in the database keep deserializing and
 * keep being listed, readable and deletable; creating one through the derive-and-validate path
 * arrives with the connector.
 */
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@Jacksonized
public class CernerFhirCredential extends Credential {

    /**
     * Vendor tenant id. Required for non-sandbox credentials; when {@link #sandbox} is true the
     * fixed sandbox tenant is filled in at save time, so the user leaves it blank.
     */
    @NotNull
    private CryptographicValue tenantKey;

    @NotNull
    private CryptographicValue clientId;

    @NotNull
    private CryptographicValue clientSecret;

    private List<String> scopes;

    /** The FHIR version that shapes the derived base URL. */
    private FhirVersion fhirVersion;

    /** Targets the vendor sandbox host with its fixed tenant rather than production. */
    private boolean sandbox;

    /** Derived at save time from the conformance statement, never user input. */
    private String tokenUrl;

    /** Derived at save time as the composed base URL, never user input. */
    private String fhirBaseUrl;

    /**
     * Compact JSON summary of the conformance statement fetched at save time, so the connection's
     * proven capabilities survive it.
     */
    private String conformance;

    protected CernerFhirCredential(CernerFhirCredentialBuilder<?, ?> builder) {
        super(builder);
        this.tenantKey = builder.tenantKey;
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.scopes = builder.scopes;
        this.fhirVersion = builder.fhirVersion;
        this.sandbox = builder.sandbox;
        this.tokenUrl = builder.tokenUrl;
        this.fhirBaseUrl = builder.fhirBaseUrl;
        this.conformance = builder.conformance;
        this.setType(CredentialType.CERNER_FHIR);
    }

    @Override
    public Credential toDeleted() {
        return markDeleted(this.toBuilder());
    }
}
