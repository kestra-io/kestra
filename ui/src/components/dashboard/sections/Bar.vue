<template>
    <div
        v-if="generated?.results?.length"
        class="chart-wrapper"
        :class="{short: props.short}"
    >
        <div
            class="chart"
            :class="{short: props.short}"
            :style="props.short ? undefined : {height: `${chartHeight}px`}"
        >
            <ChartLegend
                v-if="showLegend"
                :items="legendItems"
                :chart="ksBarRef"
                @toggle="onLegendToggle"
            />
            <KsBar
                ref="ksBarRef"
                class="canvas"
                :data="seriesData"
                :categories="categories"
                :loading="false"
                :stack="true"
                :options="echartsOption"
                :disableFeatures="[ChartFeature.AXIS_SPLITLINE]"
                :tooltipType="TooltipType.EXTERNAL"
                @echarts-click="onChartClick"
            />
        </div>
    </div>
    <KsNoData v-else :class="{empty: !props.short}" />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"

    import {ChartFeature, KsBar, TooltipType, cssVar, durationUtils, type KsChartSeriesItem} from "@kestra-io/design-system"

    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {DEFAULT_BAR_CATEGORY_LIMIT, getConsistentHEXColor, rankStackedBars, useLegendToggle} from "../composables/charts"
    import {useChartDrillDown} from "../composables/chartDrillDown"
    import ChartLegend from "./ChartLegend.vue"
    import {useTheme} from "../../../utils/utils"
    import {FilterObject} from "../../../utils/filters"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        dashboardId?: string;
        chart: Chart;
        filters?: FilterObject[];
        showDefault?: boolean;
        short?: boolean;
    }>(), {
        dashboardId: undefined,
        filters: () => [],
        showDefault: false,
        short: false,
    })

    const route = useRoute()
    const theme = useTheme()
    const {t} = useI18n()

    const {drillDown} = useChartDrillDown(props.chart)

    const {data, chartOptions} = props.chart
    const {data: generated, generate} = useChartGenerator(props.dashboardId, props)

    const aggregator = Object.entries(data?.columns ?? {}).filter(([_, v]) => v.agg)
    const isDurationAgg = () => aggregator[0][1].field === "DURATION"

    const {onLegendToggle, legendSelected} = useLegendToggle()

    const categoryKey = chartOptions?.column ?? ""
    const stackKeys = Object.entries(data?.columns ?? {})
        .filter(([key, value]) => !(value as Record<string, any>).agg && key !== categoryKey)
        .map(([key]) => key)
    const valueKey = aggregator[0][0]
    const limit = (chartOptions as any)?.limit ?? DEFAULT_BAR_CATEGORY_LIMIT

    const parsedData = computed(() =>
        rankStackedBars(generated.value?.results as Record<string, unknown>[] ?? [], {
            categoryKey,
            stackKeys,
            valueKey,
            limit,
        }),
    )

    const othersLabel = computed(() => `${t("dashboards.others")} · ${parsedData.value.othersCount}`)

    const categories = computed(() => {
        const cats = parsedData.value.categories
        return parsedData.value.othersCount > 0 ? [...cats, othersLabel.value] : cats
    })

    const seriesData = computed<KsChartSeriesItem[]>(() => {
        const hasOthers = parsedData.value.othersCount > 0
        return parsedData.value.series.map((s) => ({
            name: s.name,
            data: s.data.map((v, idx) => {
                if (hasOthers && idx === s.data.length - 1) {
                    return {value: v, itemStyle: {color: cssVar("--ks-chart-skipped")}}
                }
                return v
            }),
            barWidth: 16,
            itemStyle: {
                color: getConsistentHEXColor(theme.value, s.name),
                borderColor: cssVar("--ks-bg-surface"),
                borderWidth: 1,
                borderRadius: 2,
            },
        }))
    })

    const showLegend = computed(() => !props.short && !!chartOptions?.legend?.enabled)

    const ROW_PITCH = 30
    const MIN_CHART_HEIGHT = 200
    const chartHeight = computed(() =>
        Math.max(MIN_CHART_HEIGHT, categories.value.length * ROW_PITCH + (showLegend.value ? 56 : 24)),
    )

    const legendItems = computed(() =>
        parsedData.value.series.map((s) => ({
            label: s.name,
            color: getConsistentHEXColor(theme.value, s.name),
            count: s.data.reduce((acc, n) => acc + (typeof n === "number" ? n : (n as any).value ?? 0), 0),
        })),
    )

    function leftTruncate(s: string, max: number): string {
        return s.length <= max ? s : "…" + s.slice(s.length - (max - 1))
    }

    const echartsOption = computed((): Record<string, unknown> => {
        const showAxes = !props.short
        const axisLabelStyle = {color: cssVar("--ks-text-secondary"), fontSize: 11}

        return {
            grid: props.short
                ? {top: 2, right: 2, bottom: 2, left: 2, containLabel: false}
                : {left: "0", right: "4%", top: "5%", bottom: "3%", containLabel: true},
            xAxis: {
                type: "value",
                show: showAxes,
                axisLine: {show: true, lineStyle: {color: cssVar("--ks-border-default")}},
                axisLabel: {
                    ...axisLabelStyle,
                    ...(isDurationAgg() ? {formatter: (v: number) => durationUtils.humanDuration(v)} : {}),
                },
            },
            yAxis: {
                type: "category",
                data: categories.value,
                inverse: true,
                show: showAxes,
                axisLine: {show: false},
                axisTick: {show: false},
                axisLabel: {
                    ...axisLabelStyle,
                    margin: 14,
                    formatter: (v: string) => leftTruncate(v, 28),
                },
            },
            tooltip: props.short
                ? {show: false}
                : {
                    trigger: "axis",
                    axisPointer: {type: "none"},
                    formatter: (params: any[]) => {
                        if (!params?.length) return ""
                        const isOthers = parsedData.value.othersCount > 0 && params[0].dataIndex === categories.value.length - 1
                        const categoryName = isOthers ? t("dashboards.others") : params[0].name
                        const nonZero = params.filter((p: any) => {
                            const val = typeof p.value === "number" ? p.value : p.value?.value ?? 0
                            return val > 0
                        })
                        const rows = nonZero
                            .map((p: any) => {
                                const val = typeof p.value === "number" ? p.value : p.value?.value ?? 0
                                const formatted = isDurationAgg() ? durationUtils.humanDuration(val) : val
                                return `<span style="color:${cssVar("--ks-text-secondary")}">${p.seriesName}</span>: ${formatted}`
                            })
                            .join("<br/>")
                        const total = nonZero.reduce((acc: number, p: any) => {
                            const val = typeof p.value === "number" ? p.value : p.value?.value ?? 0
                            return acc + val
                        }, 0)
                        const formattedTotal = isDurationAgg() ? durationUtils.humanDuration(total) : total
                        return `<b>${categoryName}</b><br/>${rows}<br/><span style="color:${cssVar("--ks-text-secondary")}">${t("Total")}</span>: ${formattedTotal}`
                    },
                },
            legend: {
                show: false,
                selected: legendSelected(legendItems.value.map((item) => item.label)),
            },
        }
    })

    const ksBarRef = ref<InstanceType<typeof KsBar> | null>(null)

    const categoryColumn = computed(() =>
        (data?.columns?.[chartOptions?.column ?? ""]) as {field?: string; labelKey?: string} | undefined,
    )

    const stackColumn = computed(() => {
        const category = chartOptions?.column ?? ""
        const key = Object.entries(data?.columns ?? {})
            .find(([k, v]) => !(v as Record<string, any>).agg && k !== category)?.[0]
        return (key ? data?.columns?.[key] : undefined) as {field?: string; labelKey?: string} | undefined
    })

    function onChartClick(params: any) {
        const isOthers = parsedData.value.othersCount > 0 && params.name === othersLabel.value
        if (isOthers) return
        drillDown([
            {column: stackColumn.value, value: params.seriesName},
            {column: categoryColumn.value, value: params.name},
        ])
    }

    function refresh() {
        return generate()
    }

    defineExpose({refresh})

    watch(() => route.params.filters, () => refresh(), {deep: true})
</script>

<style scoped lang="scss">
    .chart-wrapper {
        &.short {
            height: 40px;
            overflow: hidden;
        }
    }

    .chart {
        display: flex;
        flex-direction: column;
        height: 231px;

        &.short {
            height: 40px;
        }

        .canvas {
            flex: 1;
            min-height: 0;
        }
    }

    .empty {
        min-height: 200px;
    }
</style>
