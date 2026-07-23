// Dev-only staleness check for the committed SDK.
//
// The generated SDK under src/openapi is committed to git and NOT regenerated automatically (no CI
// job rewrites it). To catch a stale checkout cheaply, this compares the OpenAPI spec hash the SDK
// was generated from (OPENAPI_SPEC_HASH, stamped by the shared plugin and re-exported from
// src/openapi/index) against the hash of the backend's live spec, and warns once if they differ.
//
// This module is only ever reached behind an `import.meta.env.DEV` guard + dynamic import (see
// index.ts), so a production build statically resolves the guard to `false`, never emits this chunk,
// and tree-shakes the whole thing away. It is also best-effort: any failure (spec unreachable, no
// Web Crypto, non-secure context) is swallowed so it can never break dev or an API call.

let alreadyChecked = false

async function sha256First16(bytes: ArrayBuffer): Promise<string> {
    const digest = await crypto.subtle.digest("SHA-256", bytes)
    return Array.from(new Uint8Array(digest))
        .map((b) => b.toString(16).padStart(2, "0"))
        .join("")
        .slice(0, 16)
}

/**
 * Fetch the backend's live OpenAPI spec, hash it, and warn (once) if it differs from the hash this
 * SDK was generated from. Runs at most once per session; never throws.
 *
 * @param committedHash the OPENAPI_SPEC_HASH stamped into the committed SDK
 * @param specUrl       URL of the backend's served spec (default: `${KESTRA_BASE_PATH}/swagger/kestra.yml`)
 * @param label        package name used in the warning (default: "@kestra-io/kestra-sdk")
 */
export async function warnIfSdkStale(
    committedHash: string,
    specUrl?: string,
    label = "@kestra-io/kestra-sdk",
): Promise<void> {
    if (alreadyChecked || !committedHash) return
    alreadyChecked = true

    try {
        if (typeof fetch !== "function" || typeof crypto?.subtle?.digest !== "function") return

        const basePath = (typeof window !== "undefined" && window.KESTRA_BASE_PATH) || ""
        const url = specUrl ?? `${basePath}/swagger/kestra.yml`

        const response = await fetch(url, {credentials: "include"})
        if (!response.ok) return

        const liveHash = await sha256First16(await response.arrayBuffer())
        if (liveHash !== committedHash) {
            console.warn(
                `[${label}] Committed SDK looks out of date with the backend's OpenAPI spec ` +
                `(SDK ${committedHash} ≠ backend ${liveHash}). ` +
                "Run `npm run generate:sdk` from ui/ and commit the regenerated src/openapi.",
            )
        }
    } catch {
        // best-effort only — never break dev or an API call over a freshness check
    }
}
