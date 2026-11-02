import {ref, type Ref} from "vue"
import {API_URL} from "../stores/api"
import {publicFetchJson} from "../utils/publicApi"
import {countUniquePluginElements, type Plugin} from "../utils/pluginUtils"

const PUBLIC_API_TIMEOUT_MS = 5000
const CACHE_KEY = "plugins.catalogCount"
const CACHE_TTL_MS = 2 * 60 * 60 * 1000

interface CachedCount {
    count: number;
    fetchedAt: number;
}

const totalPlugins = ref<number>()
let pending: Promise<void> | null = null

function readCache(): CachedCount | undefined {
    try {
        const cached = JSON.parse(localStorage.getItem(CACHE_KEY) ?? "null") as CachedCount | null
        return typeof cached?.count === "number" && typeof cached.fetchedAt === "number" ? cached : undefined
    } catch {
        return undefined
    }
}

function writeCache(count: number) {
    try {
        localStorage.setItem(CACHE_KEY, JSON.stringify({count, fetchedAt: Date.now()} satisfies CachedCount))
    } catch {
        // Storage can be unavailable (private window, blocked site data); the count is then fetched on each load.
    }
}

function fetchCount(): Promise<void> {
    return publicFetchJson<Plugin[]>(`${API_URL}/v1/plugins/subgroups`, {signal: AbortSignal.timeout(PUBLIC_API_TIMEOUT_MS)})
        .then(data => {
            const rounded = Math.floor(countUniquePluginElements(data ?? []) / 100) * 100
            if (rounded > 0) {
                totalPlugins.value = rounded
                writeCache(rounded)
            }
        })
        .catch(err => {
            console.warn("Plugin catalog count unavailable", err)
        })
}

/**
 * Catalog-wide plugin element count as shown on kestra.io, rounded down to the nearest hundred
 * (e.g. 1800). Fetched from the public API at most once every two hours per browser: a cached count
 * shows immediately, and a stale one is refreshed in the background. Undefined until a count is
 * known, and for good when the public API cannot be reached and nothing is cached.
 */
export function usePluginsCount(): {totalPlugins: Ref<number | undefined>} {
    if (!pending) {
        const cached = readCache()
        if (cached) {
            totalPlugins.value = cached.count
        }
        pending = cached && Date.now() - cached.fetchedAt < CACHE_TTL_MS ? Promise.resolve() : fetchCount()
    }
    return {totalPlugins}
}
