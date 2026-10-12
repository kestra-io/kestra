import {ref, computed, onUnmounted, watch} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"

const getAutoRefreshEnabledKey = (routeName: string) => `autoRefreshEnabled_${routeName}`

export {getAutoRefreshEnabledKey}

export function usePeriodicRefresh() {
    const route = useRoute()
    const {t} = useI18n()
    const enabledRef = ref(false)
    const refreshInterval = ref<number | null>(null)

    const enabledKey = computed(() => getAutoRefreshEnabledKey(String(route.name)))
    const tooltip = computed(() => t("auto_refresh_tooltip", {seconds: intervalSeconds.value}))
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

    const toggleRefresh = (enabled: boolean, callback: () => void) => {
        if (refreshInterval.value) clearInterval(refreshInterval.value)
        refreshInterval.value = enabled ? window.setInterval(callback, intervalSeconds.value * 1000) : null
    }

    onUnmounted(() => refreshInterval.value && clearInterval(refreshInterval.value))

    return {
        isEnabled,
        tooltip,
        toggleRefresh,
    }
}
