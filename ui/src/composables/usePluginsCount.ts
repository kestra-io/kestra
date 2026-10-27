import {ref, type Ref} from "vue"
import {API_URL} from "../stores/api"
import {publicFetchJson} from "../utils/publicApi"
import {countUniquePluginElements, type Plugin} from "../utils/pluginUtils"

const PUBLIC_API_TIMEOUT_MS = 5000

const totalPlugins = ref<number>()
let pending: Promise<void> | null = null

/**
 * Catalog-wide plugin element count as shown on kestra.io, rounded down to the
 * nearest hundred (e.g. 1800), fetched once from the public API. Undefined until
 * it loads, and for good when the public API cannot be reached.
 */
export function usePluginsCount(): {totalPlugins: Ref<number | undefined>} {
    pending ??= publicFetchJson<Plugin[]>(`${API_URL}/v1/plugins/subgroups`, {signal: AbortSignal.timeout(PUBLIC_API_TIMEOUT_MS)})
        .then(data => {
            const rounded = Math.floor(countUniquePluginElements(data ?? []) / 100) * 100
            if (rounded > 0) {
                totalPlugins.value = rounded
            }
        })
        .catch(err => {
            console.warn("Plugin catalog count unavailable", err)
        })
    return {totalPlugins}
}
