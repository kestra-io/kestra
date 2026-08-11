import {computed, ref} from "vue"
import type {FilterConfiguration} from "../utils/filterTypes"

export function useDismissedKeys(configuration: FilterConfiguration) {
    const dismissedKeys = ref<Set<string>>(new Set())

    const isDefaultVisibleKey = (key: string): boolean =>
        configuration.keys?.some(k => k.key === key && k.visibleByDefault) ?? false

    const dismissDefaultVisibleKey = (key: string) => {
        if (!isDefaultVisibleKey(key)) return
        const next = new Set(dismissedKeys.value)
        next.add(key)
        dismissedKeys.value = next
    }

    const restoreDefaultVisibleKey = (key: string) => {
        if (!dismissedKeys.value.has(key)) return
        const next = new Set(dismissedKeys.value)
        next.delete(key)
        dismissedKeys.value = next
    }

    const dismissAllDefaultVisibleKeys = () => {
        dismissedKeys.value = new Set(
            configuration.keys
                ?.filter(k => k.visibleByDefault)
                .map(k => k.key) ?? [],
        )
    }

    const resetDismissedDefaultVisibleKeys = () => {
        dismissedKeys.value = new Set()
    }

    const hasDismissedDefaultVisibleKeys = computed(() => dismissedKeys.value.size > 0)

    return {
        dismissedKeys: computed(() => dismissedKeys.value),
        hasDismissedDefaultVisibleKeys,
        isDefaultVisibleKey,
        dismissDefaultVisibleKey,
        restoreDefaultVisibleKey,
        dismissAllDefaultVisibleKeys,
        resetDismissedDefaultVisibleKeys,
    }
}
