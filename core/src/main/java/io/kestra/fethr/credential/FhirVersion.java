package io.kestra.fethr.credential;

/**
 * FHIR version a {@link CernerFhirCredential} targets, which shapes the derived base URL.
 *
 * <p>
 * Lives here rather than in the FHIR connector package because the credential is what persists it,
 * and the connector is a later wave. The connector imports it from here when it lands.
 */
public enum FhirVersion {
    R4
}
