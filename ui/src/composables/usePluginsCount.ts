import {ref, type Ref} from "vue"
import axios from "axios"
import {API_URL} from "../stores/api"
import {countUniquePluginElements, type Plugin} from "../utils/pluginUtils"
// Build-time baseline for instances that cannot reach the public API at runtime.
import {PLUGIN_CATALOG_COUNT} from "../utils/pluginCatalogCount"

const PUBLIC_API_TIMEOUT_MS = 5000

const totalPlugins = ref(PLUGIN_CATALOG_COUNT)
let pending: Promise<void> | null = null

/**
 * Catalog-wide plugin element count as shown on kestra.io, rounded down to the
 * nearest hundred (e.g. 1800), fetched once from the public API.
 */
export function usePluginsCount(): {totalPlugins: Ref<number>} {
    pending ??= axios.get<Plugin[]>(`${API_URL}/v1/plugins/subgroups`, {timeout: PUBLIC_API_TIMEOUT_MS})
        .then(({data}) => {
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
