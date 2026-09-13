import type {CredentialDetail, CredentialType} from "override/stores/credentials"

/**
 * The part of the credential form that varies by type.
 *
 * The form collects every type's fields into one flat object and this turns the relevant ones into
 * the polymorphic body the backend expects. Keeping that branching in one place is what stops the
 * create and edit paths from drifting apart, which is why the 1.x fork factored it out too.
 */
export interface CredentialFormState {
    type: CredentialType;
    /** API_KEY and BEARER_TOKEN both carry exactly one secret, so they share a field. */
    singleSecretValue: string;
    username: string;
    password: string;
    tokenUrl: string;
    tenantKey: string;
    clientId: string;
    clientSecret: string;
    scopes: string[];
    fhirVersion: string;
    sandbox: boolean;
}

/** An empty form, with the type picker defaulted to the simplest credential. */
export function emptyCredentialForm(): CredentialFormState {
    return {
        type: "API_KEY",
        singleSecretValue: "",
        username: "",
        password: "",
        tokenUrl: "",
        tenantKey: "",
        clientId: "",
        clientSecret: "",
        scopes: [],
        fhirVersion: "R4",
        sandbox: false,
    }
}

/** Fills the form from a credential being edited. Absent fields stay at their empty defaults. */
export function credentialToForm(credential: CredentialDetail): CredentialFormState {
    return {
        ...emptyCredentialForm(),
        type: credential.type,
        // Whichever single secret this type carries, if it carries one.
        singleSecretValue: credential.key ?? credential.bearerToken ?? "",
        username: credential.username ?? "",
        password: credential.password ?? "",
        tokenUrl: credential.tokenUrl ?? "",
        tenantKey: credential.tenantKey ?? "",
        clientId: credential.clientId ?? "",
        clientSecret: credential.clientSecret ?? "",
        scopes: credential.scopes ?? [],
        fhirVersion: credential.fhirVersion ?? "R4",
        sandbox: credential.sandbox ?? false,
    }
}

/**
 * Builds the type-discriminated half of the request body. The caller adds name, namespace and
 * description, which every type shares.
 *
 * Secrets cross the wire as plain strings. They are stored encrypted, but that happens server-side:
 * the request type takes plaintext precisely so the browser never has to know the stored shape.
 */
export function buildCredentialTypeFields(form: CredentialFormState): Record<string, unknown> {
    switch (form.type) {
        case "API_KEY":
            return {type: "API_KEY", key: form.singleSecretValue}
        case "BEARER_TOKEN":
            return {type: "BEARER_TOKEN", bearerToken: form.singleSecretValue}
        case "BASIC_AUTH":
            return {
                type: "BASIC_AUTH",
                username: form.username,
                password: form.password,
            }
        case "OAUTH2":
            return {
                type: "OAUTH2",
                tokenUrl: form.tokenUrl,
                tenantKey: form.tenantKey,
                clientId: form.clientId,
                clientSecret: form.clientSecret,
                scopes: form.scopes,
            }
        case "CERNER_FHIR":
            return {
                type: "CERNER_FHIR",
                // In sandbox mode the backend fills the fixed sandbox tenant, so none is sent.
                tenantKey: form.sandbox ? undefined : form.tenantKey,
                clientId: form.clientId,
                clientSecret: form.clientSecret,
                scopes: form.scopes,
                fhirVersion: form.fhirVersion,
                sandbox: form.sandbox,
            }
        default:
            throw new Error(`Unsupported credential type: ${form.type}`)
    }
}

/** The fields each type actually uses, so the form renders only those. */
export const FIELDS_BY_TYPE: Record<CredentialType, string[]> = {
    API_KEY: ["singleSecretValue"],
    BEARER_TOKEN: ["singleSecretValue"],
    BASIC_AUTH: ["username", "password"],
    OAUTH2: ["tokenUrl", "tenantKey", "clientId", "clientSecret", "scopes"],
    CERNER_FHIR: ["fhirVersion", "sandbox", "tenantKey", "clientId", "clientSecret", "scopes"],
}
