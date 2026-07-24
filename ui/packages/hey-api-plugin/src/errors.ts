/**
 * Thrown by `createConfigureClient`'s error interceptor instead of a generic normalized `Error`
 * when a request 404s against a route that only exists in Kestra Enterprise Edition — e.g. an
 * EE-only SDK method (`listAuditLogs`, `listApps`, ...) called against an OSS server.
 *
 * Carries structured fields (not just a message) so a caller can render its own "Upgrade to
 * unlock X" UI — a CLI, an internal dashboard, a bot — without parsing `error.message`.
 */
export class EnterpriseFeatureError extends Error {
    /** HTTP status of the underlying response. Always 404: the route genuinely doesn't exist on an OSS server. */
    readonly status: number
    /** Feature identifier supplied by the matching `EnterpriseFeatureConfig`, e.g. "audit-logs". */
    readonly feature: string
    /** Kestra Enterprise Edition docs page for this feature. */
    readonly docsUrl: string
    /** Contact-sales / start-a-trial link for this feature. */
    readonly contactSalesUrl: string

    constructor(options: {
        feature: string
        docsUrl: string
        contactSalesUrl: string
        status?: number
    }) {
        super(
            `'${options.feature}' is a Kestra Enterprise Edition feature and is not available on this server. `
            + `Docs: ${options.docsUrl} — Talk to us: ${options.contactSalesUrl}`,
        )
        this.name = "EnterpriseFeatureError"
        this.status = options.status ?? 404
        this.feature = options.feature
        this.docsUrl = options.docsUrl
        this.contactSalesUrl = options.contactSalesUrl
    }
}

/**
 * Thrown by `createConfigureClient`'s error interceptor instead of {@link EnterpriseFeatureError}
 * when a request 404s against a route in the EE-only registry, but the server's response confirms
 * it *is* an Enterprise Edition server (via the `X-Kestra-Edition: EE` response header). A real EE
 * server missing a route the SDK expects means the SDK and server versions are out of sync — not
 * that the feature requires an upgrade, which is why this is a distinct type with different
 * messaging rather than a variant of `EnterpriseFeatureError`'s "talk to sales" copy.
 */
export class SdkVersionMismatchError extends Error {
    /** HTTP status of the underlying response. Always 404. */
    readonly status: number
    /** Feature identifier supplied by the matching `EnterpriseFeatureConfig`, e.g. "audit-logs". */
    readonly feature: string

    constructor(options: { feature: string; status?: number }) {
        super(
            `Route for the '${options.feature}' feature was not found on this server, but the server reports itself `
            + "as Kestra Enterprise Edition. This usually means your Kestra SDK and server versions are out of sync — "
            + "check that both are on compatible versions.",
        )
        this.name = "SdkVersionMismatchError"
        this.status = options.status ?? 404
        this.feature = options.feature
    }
}

/** A route matched as Enterprise-Edition-only. */
export interface EnterpriseFeatureMatch {
    /** Human-readable feature identifier, e.g. "audit-logs". Passed to `docsUrl`/`contactSalesUrl`. */
    feature: string
}

/**
 * Injected into `createConfigureClient` so the shared runtime stays agnostic of which routes are
 * Enterprise-only — that registry is a per-SDK-consumer concern (only client-sdk, the single
 * published SDK spanning both editions, needs one; the OSS/EE UI SDKs can omit this entirely).
 */
export interface EnterpriseFeatureConfig {
    /**
     * Given the request method and the *templated* OpenAPI path (e.g.
     * "/api/v1/{tenant}/audit-logs/search", not the resolved URL with real path params
     * substituted in), return a match if this route is Enterprise-Edition-only. Return undefined
     * for anything else, including ordinary "resource not found" 404s — those must keep raising
     * the generic normalized error below, not an EnterpriseFeatureError.
     */
    matchRoute: (method: string, path: string) => EnterpriseFeatureMatch | undefined
    /** Build the docs URL for a matched feature. */
    docsUrl: (feature: string) => string
    /** Build the contact-sales/trial URL for a matched feature. */
    contactSalesUrl: (feature: string) => string
}
