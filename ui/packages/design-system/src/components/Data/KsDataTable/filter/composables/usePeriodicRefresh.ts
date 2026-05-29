import {ref, computed, onUnmounted, watch, type ComputedRef, type WritableComputedRef} from "vue"
import {useRoute} from "vue-router"

const getAutoRefreshEnabledKey = (routeName: string) => `autoRefreshEnabled_${routeName}`

export {getAutoRefreshEnabledKey}

export function usePeriodicRefresh(): {
    isEnabled: WritableComputedRef<boolean>;
    tooltip: ComputedRef<string>;
    toggleRefresh: (enabled: boolean, callback: () => void) => void;
} {
    const route = useRoute()
    const enabledRef = ref(false)
    const refreshInterval = ref<number | null>(null)

    const enabledKey = computed(() => getAutoRefreshEnabledKey(String(route.name)))
    const tooltip = computed(() => `Auto refresh every ${intervalSeconds.value} seconds`)
    const intervalSeconds = computed(() => parseInt(localStorage.getItem("autoRefreshInterval") ?? "10"))

    watch(enabledKey, () => {
        enabledRef.value = localStorage.getItem(enabledKey.value) === "true"
    }, {immediate: true})

    const isEnabled = computed({
        get: () => enabledRef.value,
        set: (value: boolean) => {
            enabledRef.value = value
            localStorage.setItem(enabledKey.value, String(value))
        },
    })

    const toggleRefresh = (enabled: boolean, callback: () => void): void => {
        if (refreshInterval.value) clearInterval(refreshInterval.value)
        refreshInterval.value = enabled ? window.setInterval(callback, intervalSeconds.value * 1000) : null
    }

    onUnmounted(() => refreshInterval.value && clearInterval(refreshInterval.value))

    return {
        isEnabled: isEnabled,
        tooltip: tooltip,
        toggleRefresh: toggleRefresh,
    }
}
