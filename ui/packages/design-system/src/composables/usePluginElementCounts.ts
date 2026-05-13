import {computed, isRef} from "vue"
import type {MaybeRef} from "vue"
import {extractPluginElements, type Plugin} from "../utils/plugins"

export function usePluginElementCounts(plugin: MaybeRef<Plugin | undefined>) {
    const elementsByType = computed(() => {
        const pluginValue = isRef(plugin) ? plugin.value : plugin
        return pluginValue ? extractPluginElements(pluginValue) : {}
    })

    const total = computed(() =>
        Object.values(elementsByType.value).reduce((sum, arr) => sum + arr.length, 0),
    )

    return {elementsByType, total} as const
}
