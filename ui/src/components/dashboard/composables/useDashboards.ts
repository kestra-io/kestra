import {onMounted, onBeforeUnmount, computed, ref} from "vue"

import {useRoute} from "vue-router"

import {useDashboardStore} from "../../../stores/dashboard"

import {useI18n} from "vue-i18n"

import {decodeSearchParams} from "@kestra-io/design-system"

import {Chart} from "../types.ts"
import {ChartFiltersOverrides, QueryFilter} from "@kestra-io/kestra-sdk"



export const isKPIChart = (type: string): boolean => type === "io.kestra.plugin.core.dashboard.chart.KPI"

export const isMarkdownChart = (type: string): boolean => type === "io.kestra.plugin.core.dashboard.chart.Markdown"

export const isExportableChart = (type: string): boolean => !isMarkdownChart(type)

export const getChartTitle = (chart: Chart): string => chart.chartOptions?.displayName ?? chart.id

export const getPropertyValue = (data: Record<string, any>, property: "value" | "description"): string => data.results?.[0]?.[property]

export const isPaginationEnabled = (chart: Chart): boolean => chart.chartOptions?.pagination?.enabled ?? false

export const processFlowYaml = (yaml: string, namespace: string, flow: string): string => yaml.replace(/--NAMESPACE--/g, namespace).replace(/--FLOW--/g, flow)

export const ALLOWED_CREATION_ROUTES = ["home", "flows/update", "namespaces/update"]

export function useChartGenerator(dashboardId: string | undefined, props: {chart: Chart; filters: QueryFilter[]; showDefault: boolean;}, includeHooks: boolean = true) {
    const percentageShown = computed(() => props.chart?.chartOptions?.numberType === "PERCENTAGE")

    const route = useRoute()

    const dashboardStore = useDashboardStore()

    const {t} = useI18n({useScope: "global"})
    const EMPTY_TEXT = t("dashboards.empty")

    const data = ref()
    let isMounted = true
    onBeforeUnmount(() => {
        isMounted = false
    })

    async function generate(pagination?: { pageNumber: number; pageSize: number }, customFilters?: QueryFilter[], appendFilters?: QueryFilter[]) {
        const filters = customFilters ?? props.filters.concat(decodeSearchParams(route.query) as QueryFilter[] ?? [])
        const allFilters = (appendFilters?.length ? [...filters, ...appendFilters] : filters)
        const parameters: ChartFiltersOverrides = {...pagination, filters: (allFilters ?? {})}

        let result
        if (!props.showDefault) {
            if(!dashboardId){
                throw new Error("to generate charts from backend we need a dashboard id")
            }
            result = await dashboardStore.generate(dashboardId, props.chart.id, parameters)
        } else {
            if (!props.chart.content){
                throw new Error("Chart content must exist for preview.")
            }

            result = await dashboardStore.chartPreview({
                chart: props.chart.content,
                globalFilter: parameters,
            })
        }

        if (!isMounted) return
        data.value = result
        return data.value
    };

    onMounted(async () => {
        if (includeHooks) await generate()
    })

    return {percentageShown, EMPTY_TEXT, data, generate}
}

export * from "../types"