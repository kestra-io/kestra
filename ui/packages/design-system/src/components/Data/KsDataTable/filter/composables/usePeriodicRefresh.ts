import {ref, computed, onUnmounted, watch} from "vue"
import {useRoute} from "vue-router"
import {useI18n} from "vue-i18n"

const getAutoRefreshEnabledKey = (routeName: string) => `autoRefreshEnabled_${routeName}`

export {getAutoRefreshEnabledKey}

const enabledRef = ref(false)

export function usePeriodicRefresh(refreshCallback?: () => void) {
    const route = useRoute()
    const {t} = useI18n()
    const refreshInterval = ref<number | null>(null)

    const enabledKey = computed(() => getAutoRefreshEnabledKey(String(route.name)))
    const tooltip = computed(() => t("filter.auto_refresh_tooltip", {seconds: intervalSeconds.value}))
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
