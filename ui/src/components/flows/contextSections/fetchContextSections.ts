import {resolveTenant} from "@kestra-io/kestra-sdk/shared"
import type {ContextSectionInput, ContextSectionProvider, DataSection} from "./types"

// Deliberately never invalidated within a session: a KV key/secret/file added after the editor was
// opened only appears after a reload. `resetContextSectionsCache` exists for tests, not for runtime use.
const cache = new Map<string, Promise<DataSection[]>>()

export function resetContextSectionsCache(): void {
    cache.clear()
}

async function safelyRun(provider: ContextSectionProvider, input: ContextSectionInput): Promise<DataSection | null> {
    try {
        return await provider(input)
    } catch (error) {
        console.error("Context section provider failed", error)
        return null
    }
}

export async function fetchContextSections(
    providers: ContextSectionProvider[],
    input: ContextSectionInput,
): Promise<DataSection[]> {
    // The SDK's tenant is a mutable module-level singleton (switching tenants doesn't reload the
    // page), so it's resolved fresh on every call rather than trusted from a caller-supplied field —
    // otherwise the cache key below can't tell two tenants' entries for the same namespace apart.
    const tenant = resolveTenant(input.tenant)
    const cacheKey = `${tenant}::${input.namespace}`
    const cached = cache.get(cacheKey)
    if (cached) return cached

    const resolvedInput = {...input, tenant}
    const promise = Promise.all(providers.map(provider => safelyRun(provider, resolvedInput)))
        .then(results => results.filter((section): section is DataSection => section !== null))

    cache.set(cacheKey, promise)
    return promise
}
