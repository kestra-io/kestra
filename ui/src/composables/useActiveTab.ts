import {computed, type ComputedRef} from "vue"
import {useRoute} from "vue-router"

/**
 * Resolves the currently active tab name.
 *
 * Tab identity now lives in the matched child route (`route.meta.tab`) so that
 * `<router-view>` can pick the right component. For pages that have not yet been
 * migrated to child routes we fall back to the legacy `:tab` route param, keeping
 * every existing `route.params.tab` reader working during the roll-out.
 */
export function useActiveTab(): ComputedRef<string | undefined> {
    const route = useRoute()
    return computed<string | undefined>(
        () => (route?.meta?.tab as string | undefined) ?? (route?.params?.tab as string | undefined),
    )
}
