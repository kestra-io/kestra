import {computed, type ComputedRef} from "vue"
import {useMiscStore} from "override/stores/misc"

export const DEFAULT_SYSTEM_NAMESPACE = "system"

/**
 * Resolves the instance's system namespace, falling back to `system` when the
 * backend config is missing or blank.
 *
 * A blank value is treated as absent on purpose: an empty namespace would build
 * routes with an empty id and never match an equality check against a real
 * namespace.
 */
export function useSystemNamespace(): ComputedRef<string> {
    const miscStore = useMiscStore()

    return computed(() => {
        const configured = miscStore.configs?.systemNamespace
        return typeof configured === "string" && configured.trim() ? configured : DEFAULT_SYSTEM_NAMESPACE
    })
}
