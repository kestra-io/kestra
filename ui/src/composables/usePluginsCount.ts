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
let started = false

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
    }
}

function fetchCount() {
    publicFetchJson<Plugin[]>(`${API_URL}/v1/plugins/subgroups`, {signal: AbortSignal.timeout(PUBLIC_API_TIMEOUT_MS)})
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

export function usePluginsCount(): {totalPlugins: Ref<number | undefined>} {
    if (!started) {
        started = true
        const cached = readCache()
        if (cached) {
            totalPlugins.value = cached.count
        }
        if (!cached || Date.now() - cached.fetchedAt >= CACHE_TTL_MS) {
            fetchCount()
        }
    }
    return {totalPlugins}
}
