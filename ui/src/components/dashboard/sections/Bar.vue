<template>
    <div
        v-if="generated !== undefined"
        :class="[props.short ? 'short-chart' : 'chart', (!props.short && chartOptions?.legend?.enabled) ? 'with-legend' : '']"
    >
        <KsBar
            ref="ksBarRef"
            :data="seriesData"
            :categories="categories"
            :loading="false"
            :stack="true"
            :options="echartsOption"
            :disableFeatures="[ChartFeature.AXIS_SPLITLINE]"
            :tooltipType="TooltipType.EXTERNAL"
        />
    </div>
    <KsEmpty v-else />
</template>

<script setup lang="ts">
    import {computed, ref, watch, PropType} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {ChartFeature, KsBar, TooltipType, durationUtils} from "@kestra-io/design-system"
    import type {KsChartSeriesItem} from "@kestra-io/design-system"
    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {extractState, getConsistentHEXColor} from "../composables/charts"
    import {useTheme} from "../../../utils/utils"
    import {FilterObject} from "../../../utils/filters"
    import {useMiscStore} from "override/stores/misc"

    defineOptions({inheritAttrs: false})
    const props = defineProps({
        dashboardId: {type: String, required: false, default: undefined},
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
        short: {type: Boolean, default: false},
    })

    const {data, chartOptions} = props.chart

    const aggregator = Object.entries(data?.columns ?? {}).filter(([_, v]) => v.agg)

    const theme = useTheme()

    function isDurationAgg() {
        return aggregator[0][1].field === "DURATION"
    }

    const parsedData = computed(() => {
        const column = chartOptions?.column ?? ""
        const columns = data?.columns ?? {}

        const validColumns = Object.entries(columns)
            .filter(([_, value]) => !(value as Record<string, any>).agg)
            .filter(c => c[0] !== column)
            .map(([key]) => key)

        const grouped: Record<string, Record<string, number>> = {}

        const rawData = generated.value?.results as Record<string, any>[] | undefined
        rawData?.forEach((item: Record<string, any>) => {
            const key = validColumns.map((col) => item[col]).join(", ")
            const itemColumn = item[column] as string

            if (!grouped[itemColumn]) {
                grouped[itemColumn] = {}
            }
            if (!grouped[itemColumn][key]) {
                grouped[itemColumn][key] = 0
            }

            grouped[itemColumn][key] += item[aggregator[0][0]]
        })

        const xLabels = [...new Set(rawData?.map((item: Record<string, any>) => item[column] as string))]

        const datasets = xLabels.flatMap((xLabel) => {
            return Object.entries(grouped[xLabel as string] ?? {}).map(subSectionsEntry => ({
                label: subSectionsEntry[0],
                data: xLabels.map(label => xLabel === label ? subSectionsEntry[1] : 0),
                backgroundColor: getConsistentHEXColor(theme.value, subSectionsEntry[0]),
                tooltipText: `(${subSectionsEntry[0]}): ${aggregator[0][0]} = ${(isDurationAgg() ? durationUtils.humanDuration(subSectionsEntry[1]) : subSectionsEntry[1])}`,
            }))
        })

        return {labels: xLabels, datasets}
    })

    const categories = computed(() => parsedData.value.labels)

    const seriesData = computed<KsChartSeriesItem[]>(() => {
        return parsedData.value.datasets.map((ds) => ({
            name: ds.label,
            data: ds.data,
            itemStyle: {color: ds.backgroundColor},
        }))
    })

    const echartsOption = computed((): Record<string, unknown> => {
        const isCompact = props.short
        const showAxes = !isCompact

        return {
            grid: isCompact
                ? {top: 2, right: 2, bottom: 2, left: 2, containLabel: false}
                : {left: "3%", right: "4%", bottom: "3%", top: chartOptions?.legend?.enabled ? "60px" : "5%", containLabel: true},
            xAxis: {
                type: "category",
                data: categories.value,
                show: showAxes,
                name: showAxes ? (chartOptions?.column ? (data?.columns?.[chartOptions.column]?.displayName ?? chartOptions.column) : "") : undefined,
                nameTextStyle: {align: "right"},
            },
            yAxis: {
                type: "value",
                show: showAxes,
                name: showAxes ? (aggregator[0][1].displayName ?? aggregator[0][0]) : undefined,
                nameTextStyle: {align: "left"},
                axisLabel: isDurationAgg()
                    ? {formatter: (v: number) => durationUtils.humanDuration(v)}
                    : {},
            },
            tooltip: props.short
                ? {show: false}
                : {trigger: "axis", axisPointer: {type: "shadow"}},
            legend: (!isCompact && chartOptions?.legend?.enabled) ? {
                top: "10px",
                right: "10px",
            } : {show: false},
        }
    })

    const {data: generated, generate} = useChartGenerator(props.dashboardId, props)

    const ksBarRef = ref<InstanceType<typeof KsBar> | null>(null)
    const route = useRoute()
    const router = useRouter()

    watch(ksBarRef, (newRef) => {
        if (!newRef) return
        const instance = newRef.getEchartsInstance()
        if (!instance) return
        instance.on("click", (params: any) => {
            const state = params.seriesName
            const query: Record<string, any> = {}
            if (state) {
                query.state = extractState(state)
                query.scope = "USER"
                query.size = 100
                query.page = 1
            }
            if (route.query.namespace) query.namespace = route.query.namespace
            if (route.query.q) query.q = route.query.q

            router.push({
                name: "executions/list",
                params: {tenant: route.params.tenant},
                query: {
                    ...query,
                    "filters[timeRange][EQUALS]": useMiscStore()?.configs?.chartDefaultDuration ?? "PT24H",
                },
            })
        })
    })

    function refresh() {
        return generate()
    }

    defineExpose({
        refresh,
    })

    watch(() => route.params.filters, () => {
        refresh()
    }, {deep: true})
</script>

<style scoped lang="scss">
    .chart {
        height: 231px;

        &.with-legend {
            height: 200px;
        }
    }

    .short-chart {
        height: 40px;
    }
</style>
