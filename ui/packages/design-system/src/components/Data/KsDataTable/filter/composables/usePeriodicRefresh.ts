import {ref, computed, onUnmounted, watch} from "vue"
import {useRoute} from "vue-router"

const getAutoRefreshEnabledKey = (routeName: string) => `autoRefreshEnabled_${routeName}`

export {getAutoRefreshEnabledKey}

// The flag is shared by every instance so the page-lifetime interval owner (the filter bar)
// and the settings panel stay in sync even while the panel is unmounted.
const enabledRef = ref(false)

export function usePeriodicRefresh(refreshCallback?: () => void) {
    const route = useRoute()
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

    // Only the caller that owns the refresh callback (the filter bar, not the settings
    // popover) manages the interval, so closing the popover cannot stop it.
    if (refreshCallback) {
        watch(isEnabled, (enabled) => {
            if (refreshInterval.value) clearInterval(refreshInterval.value)
            refreshInterval.value = enabled ? window.setInterval(refreshCallback, intervalSeconds.value * 1000) : null
        }, {immediate: true})

        onUnmounted(() => refreshInterval.value && clearInterval(refreshInterval.value))
    }

    return {
        isEnabled,
        tooltip,
    }
}
