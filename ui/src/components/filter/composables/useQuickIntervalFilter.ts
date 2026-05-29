import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useI18n} from "vue-i18n"
import {decodeSearchParams, normalizeRouteTimeRangeFilter} from "@kestra-io/design-system"

export const useQuickIntervalFilter = () => {
    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()

    const quickIntervals = computed(() => [
        {label: t("datepicker.short.15m"), value: "PT15M"},
        {label: t("datepicker.short.1h"), value: "PT1H"},
        {label: t("datepicker.short.12h"), value: "PT12H"},
        {label: t("datepicker.short.1d"), value: "PT24H"},
        {label: t("datepicker.short.7d"), value: "PT168H"},
    ])

    const selectedTimeRange = computed<string | undefined>(() => {
        if (route.query.timeRange) {
            return route.query.timeRange as string
        }
        const decoded = decodeSearchParams(route.query)
        const tr = decoded.find((item) => item?.field === "timeRange")
        const raw = tr?.value
        if (Array.isArray(raw)) {
            return raw[0] as string | undefined
        }
        return raw as string | undefined
    })

    const onQuickFilterTimeRange = (value: string) => {
        router.replace({query: normalizeRouteTimeRangeFilter(route.query, value)})
    }

    return {quickIntervals, selectedTimeRange, onQuickFilterTimeRange}
}
