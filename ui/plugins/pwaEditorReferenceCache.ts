// What the flow editor reads to validate, autocomplete and document a flow: the plugin list, schemas, icons and per-type documentation.
// Install jobs, plugin UI remotes and task endpoints are left out, a stale copy of those would be wrong rather than merely old.
export const EDITOR_REFERENCE_PATTERN = /\/api\/v1\/(?:[^/]+\/)?plugins(?:\/(?:schemas|properties|inputs|icons|groups)(?:\/[^?]*)?|\/(?:[\w$-]+\.)+[\w$-]+(?:\/versions(?:\/[^/?]+)?)?)?(?:\?.*)?$/

export const editorReferenceCache = {
    urlPattern: EDITOR_REFERENCE_PATTERN,
    // Not StaleWhileRevalidate: online, the server's ETag revalidation must keep winning so an installed plugin shows up at once (#12102).
    handler: "NetworkFirst" as const,
    options: {
        cacheName: "kestra-editor-reference",
        networkTimeoutSeconds: 3,
        cacheableResponse: {statuses: [200]},
        expiration: {maxEntries: 500},
    },
}
