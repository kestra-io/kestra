import {resolveTenant} from "@kestra-io/kestra-sdk/shared"
import type {ContextSectionInput, ContextSectionProvider, DataSection, ProviderSection} from "./types"

// Cached per namespace+tenant for the session: a KV key/secret/file added after the editor was opened
// only appears after a reload. `resetContextSectionsCache` exists for tests, not for runtime use.
type ProviderResult = {status: "ok"; section: ProviderSection | null} | {status: "error"}

const cache = new Map<string, Promise<ProviderResult[]>>()

export function resetContextSectionsCache(): void {
    cache.clear()
}

async function safelyRun(provider: ContextSectionProvider, input: ContextSectionInput): Promise<ProviderResult> {
    try {
        return {status: "ok", section: await provider(input)}
    } catch (error) {
        console.error("Context section provider failed", error)
        return {status: "error"}
    }
}

export async function fetchContextSections(
    providers: ContextSectionProvider[],
    input: ContextSectionInput,
    t: (key: string) => string,
): Promise<DataSection[]> {
    // The SDK's tenant is a mutable module-level singleton (switching tenants doesn't reload the
    // page), so it's resolved fresh on every call rather than trusted from a caller-supplied field —
    // otherwise the cache key below can't tell two tenants' entries for the same namespace apart.
    const tenant = resolveTenant(input.tenant)
    const cacheKey = `${tenant}::${input.namespace}`
    let cached = cache.get(cacheKey)
    if (!cached) {
        const resolvedInput = {...input, tenant}
        cached = Promise.all(providers.map(provider => safelyRun(provider, resolvedInput)))
        cache.set(cacheKey, cached)
        // A failed provider must not permanently drop its section from the session-long cache — the
        // next call retries everything fresh instead of replaying today's transient failure forever.
        cached.then(results => {
            if (results.some(result => result.status === "error")) cache.delete(cacheKey)
        })
    }

    const results = await cached
    return results
        .filter((result): result is {status: "ok"; section: ProviderSection | null} => result.status === "ok")
        .map(result => result.section)
        .filter((section): section is ProviderSection => section !== null)
        .map(({labelKey, ...section}) => ({...section, label: t(labelKey)}))
}
