<template>
    <div
        v-if="generated?.results?.length"
        class="chart"
        :class="{short: props.short}"
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
    <KsTableEmpty v-else :class="{empty: !props.short}" />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"

    import {ChartFeature, KsBar, TooltipType, cssVar, durationUtils, type KsChartSeriesItem} from "@kestra-io/design-system"

    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {getConsistentHEXColor, useLegendToggle} from "../composables/charts"
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

    const {drillDown} = useChartDrillDown(props.chart)

    const {data, chartOptions} = props.chart
    const {data: generated, generate} = useChartGenerator(props.dashboardId, props)

    const aggregator = Object.entries(data?.columns ?? {}).filter(([_, v]) => v.agg)
    const isDurationAgg = () => aggregator[0][1].field === "DURATION"

    const {onLegendToggle, legendSelected} = useLegendToggle()

    const parsedData = computed(() => {
        const column = chartOptions?.column ?? ""
        const columns = data?.columns ?? {}

        const stackColumns = Object.entries(columns)
            .filter(([key, value]) => !(value as Record<string, any>).agg && key !== column)
            .map(([key]) => key)

        const grouped: Record<string, Record<string, number>> = {}
        const rawData = generated.value?.results as Record<string, any>[] | undefined

        rawData?.forEach((item) => {
            const stack = stackColumns.map((col) => item[col]).join(", ")
            const xLabel = item[column] as string

            grouped[xLabel] ??= {}
            grouped[xLabel][stack] = (grouped[xLabel][stack] ?? 0) + item[aggregator[0][0]]
        })

        const xLabels = [...new Set(rawData?.map((item) => item[column] as string))]

        const datasets = xLabels.flatMap((xLabel) =>
            Object.entries(grouped[xLabel as string] ?? {}).map(([label, value]) => ({
                label,
                data: xLabels.map((x) => (x === xLabel ? value : 0)),
                backgroundColor: getConsistentHEXColor(theme.value, label),
            })),
        )

        return {labels: xLabels, datasets}
    })

    const categories = computed(() => parsedData.value.labels)

    const seriesData = computed<KsChartSeriesItem[]>(() =>
        parsedData.value.datasets.map((ds) => ({
            name: ds.label,
            data: ds.data,
            barWidth: 12,
            itemStyle: {
                color: ds.backgroundColor,
                borderColor: cssVar("--ks-bg-surface"),
                borderWidth: 1,
                borderRadius: 1,
            },
        })),
    )

    const showLegend = computed(() => !props.short && !!chartOptions?.legend?.enabled)

    const legendItems = computed(() =>
        parsedData.value.datasets.map((ds) => ({
            label: ds.label,
            color: ds.backgroundColor,
            count: (ds.data as number[]).reduce((acc, n) => acc + (n || 0), 0),
        })),
    )

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
                axisLabel: {...axisLabelStyle, margin: 24},
            },
            tooltip: props.short
                ? {show: false}
                : {trigger: "axis", axisPointer: {type: "none"}},
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
