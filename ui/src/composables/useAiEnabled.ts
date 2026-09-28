import {computed, type ComputedRef} from "vue"
import type {NavigationGuardReturn} from "vue-router"
import {useMiscStore} from "override/stores/misc"

/**
 * Whether the AI Copilot is enabled on this instance (`kestra.ai.enabled`). Unknown until the configs
 * load, and treated as enabled until then so the Copilot entry points don't flicker in.
 */
export function useAiEnabled(): ComputedRef<boolean> {
    const miscStore = useMiscStore()
    return computed(() => miscStore.configs?.isAiEnabled !== false)
}

export async function aiEnabledGuard(): Promise<NavigationGuardReturn> {
    const miscStore = useMiscStore()
    try {
        const configs = miscStore.configs ?? await miscStore.loadConfigs()
        return configs?.isAiEnabled === false ? {name: "home"} : true
    } catch {
        return true
    }
}
