import {EDITOR_REFERENCE_PATTERN} from "../src/utils/editorReferenceRequests.ts"

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
