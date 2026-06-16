<template>
    <div
        v-if="generated?.total > 0"
        class="chart"
        :class="{short: props.short, execution: props.execution}"
    >
        <ChartLegend
            v-if="showLegend"
            :items="legendStatuses"
            :durationLabel="yBShown ? durationLabel : undefined"
            :chart="ksEchartRef"
            @toggle="onLegendToggle"
        />

        <KsEchart
            ref="ksEchartRef"
            class="canvas"
            :options="echartsOption"
            :loading="false"
            :tooltipType="TooltipType.EXTERNAL"
            @echarts-click="onChartClick"
        />
    </div>
    <KsTableEmpty
        v-else-if="!props.short || (props.execution && generated?.total === 0)"
        :class="{empty: !props.short && !props.execution}"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"

    import moment from "moment"
    import {use, graphic} from "echarts/core"
    import {BarChart, LineChart} from "echarts/charts"
    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    import {KsEchart, TooltipType, cssVar, durationUtils} from "@kestra-io/design-system"

    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {getConsistentHEXColor, useLegendToggle} from "../composables/charts"
    import {useChartDrillDown} from "../composables/chartDrillDown"
    import ChartLegend from "./ChartLegend.vue"
    import {getDateFormat, useTheme} from "../../../utils/utils"
    import {FilterObject} from "../../../utils/filters"

    use([BarChart, LineChart])

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        dashboardId?: string;
        chart: Chart;
        filters?: FilterObject[];
        showDefault?: boolean;
        short?: boolean;
        execution?: boolean;
        flow?: string;
        namespace?: string;
    }>(), {
        dashboardId: undefined,
        filters: () => [],
        showDefault: false,
        short: false,
        execution: false,
        flow: undefined,
        namespace: undefined,
    })

    const route = useRoute()
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")

    const {drillDown} = useChartDrillDown(props.chart)

    const {data, chartOptions} = props.chart

    const aggregator = computed(() => {
        return Object.entries(data?.columns ?? {})
            .filter(([_, v]) => v.agg)
            .sort((a, b) => {
                const aStyle = a[1].graphStyle || ""
                const bStyle = b[1].graphStyle || ""
                return aStyle.localeCompare(bStyle)
            })
    })

    const yBShown = computed(() => aggregator.value.length === 2)

    const theme = useTheme()

    const {onLegendToggle, legendSelected} = useLegendToggle()

    function isDuration(field: string | undefined): boolean {
        return field === "DURATION"
    }

    const parseValue = (value: unknown): unknown => {
        const date = moment(value as moment.MomentInput, moment.ISO_8601, true)
        const query = {
            ...Object.fromEntries(
                props.filters.map(({field, value: filterValue, operation}) =>
                    [`filters[${field}][${operation}]`, filterValue]),
            ),
            ...route.query,
        }
        return date.isValid() ? date.format(getDateFormat(
            (route.query.startDate ?? query["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"]) as string | undefined,
            (route.query.endDate ?? query["filters[endDate][LESS_THAN_OR_EQUAL_TO]"]) as string | undefined,
            query["filters[timeRange][EQUALS]"] as string | undefined,
        )) : value
    }

    const shortAxisLabel = (value: string): string => {
        if (typeof value !== "string") return value
        const [datePart, ...timeParts] = value.split(":")
        if (timeParts.length) return timeParts.join(":")
        const segments = datePart.split("-")
        return segments.length === 3 ? segments.slice(1).join("-") : datePart
    }

    const parsedData = computed(() => {
        const rawData = generated.value.results as Record<string, any>[] | undefined
        const xAxis = (() => {
            const values = rawData?.map((v: Record<string, any>) => {
                return parseValue(v[chartOptions?.column ?? ""])
            })

            return Array.from(new Set(values)).sort()
        })()

        const aggregatorKeys = aggregator.value.map(([key]) => key)

        const reducer = (array: Record<string, any>[] | undefined, field: string, yAxisID: string) => {
            if (!array?.length) return

            const columns = data?.columns ?? {}
            const column = chartOptions?.column ?? ""
            const colorByColumn = (chartOptions as Record<string, any>)?.colorByColumn as string | undefined

            // Get the fields for stacks (columns without `agg` and not the xAxis column)
            const fields = Object.keys(columns)
                .filter(key => !aggregatorKeys.includes(key))
                .filter(key => key !== column)

            return array.reduce((acc: any, {...params}) => {
                const stack = fields.map((f) => params[f]).join(", ")

                if (!acc[stack]) {
                    acc[stack] = {
                        type: "bar",
                        yAxisID,
                        data: [],
                        tooltip: stack,
                        label: colorByColumn ? params[colorByColumn] : undefined,
                        backgroundColor: getConsistentHEXColor(
                            theme.value,
                            colorByColumn ? params[colorByColumn] : undefined,
                        ),
                        unique: new Set(),
                    }
                }

                const current = acc[stack]
                const parsedDate = parseValue(params[column])

                // Check if the date is already processed
                if (!current.unique.has(parsedDate)) {
                    current.unique.add(parsedDate)
                    current.data.push({
                        x: parsedDate,
                        y: params[field],
                    })
                } else {
                    // Update existing stack value for the same date
                    const existing = current.data.find((v: {x: unknown; y: number}) => v.x === parsedDate)
                    if (existing) existing.y += params[field]
                }

                return acc
            }, {})
        }

        const getData = (_field: string, object: Record<string, any> = {}) => {
            return Object.values(object).map((dataset: any) => {
                const datasetData = xAxis.map((xAxisLabel) => {
                    const temp = dataset.data.find((v: {x: unknown; y: number}) => v.x === xAxisLabel)
                    return temp ? temp.y : 0
                })

                return {...dataset, data: datasetData}
            })
        }

        const yDataset = reducer(rawData, aggregator.value[0][0], "y")

        // Sorts the dataset array alphabetically by label for a consistent order across time ranges.
        const yDatasetData = Object.values(getData(aggregator.value[0][0], yDataset)).sort((a: any, b: any) =>
            (a.label ?? "").localeCompare(b.label ?? ""),
        )

        const label = aggregator.value?.[1]?.[1]?.displayName ?? aggregator.value?.[1]?.[1]?.field

        let duration: number[] = []
        if(yBShown.value){
            const helper = Array.from(new Set(rawData?.map((v: Record<string, any>) => parseValue(v.date)))).sort()

            // Step 1: Group durations by formatted date
            const groupedDurations: Record<string, number> = {}
            rawData?.forEach((item: Record<string, any>) => {
                const formattedDate = parseValue(item.date) as string
                groupedDurations[formattedDate] = (groupedDurations[formattedDate] || 0) + item.duration
            })

            // Step 2: Map to target dates
            duration = helper.map(date => groupedDurations[date as string] || 0)
        }

        return {
            labels: xAxis,
            datasets: yBShown.value
                ? [
                    {
                        yAxisID: "yB",
                        type: "line",
                        data: duration,
                        label: label,
                        borderColor: cssVar("--ks-chart-duration"),
                        smooth: false,
                        areaStyle: {
                            color: new graphic.LinearGradient(0, 0, 0, 1, [
                                {
                                    offset: 0,
                                    color: cssVar("--ks-chart-duration", 0.3),
                                },
                                {
                                    offset: 1,
                                    color: cssVar("--ks-chart-duration", 0),
                                },
                            ]),
                        },
                    },
                    ...yDatasetData,
                ]
                : yDatasetData,
        }
    })

    const echartsOption = computed((): Record<string, unknown> => {
        const pd = parsedData.value
        const xAxisData = pd.labels as string[]
        const isCompact = props.short || props.execution
        const showAxes = !isCompact && !verticalLayout.value

        const barDatasets = (pd.datasets as any[]).filter((ds) => ds.type !== "line")
        const radius = props.short ? 0.5 : 2

        /**
         * ECharts has no native gap for stacked segments — faked with a transparent border.
         * Lowest non-zero segment per x gets a flat bottom to sit on the axis; rest are pills.
         */
        const barSeries = barDatasets.map((ds, index) => ({
            type: "bar",
            name: ds.label,
            stack: "total",
            yAxisIndex: 0,
            data: (ds.data as number[]).map((value, x) => ({
                value,
                itemStyle: {
                    borderRadius: index === barDatasets.findIndex((d) => (d.data[x] ?? 0) > 0)
                        ? [radius, radius, 0, 0]
                        : radius,
                },
            })),
            itemStyle: {
                color: ds.backgroundColor,
                borderColor: "transparent",
                borderWidth: props.short ? 0 : 2,
            },
            barMaxWidth: props.short ? 6 : props.execution ? 24 : 48,
            ...(props.short ? {barCategoryGap: "0%"} : {}),
        }))

        const lineSeries = (pd.datasets as any[])
            .filter((ds) => ds.type === "line")
            .map((ds) => ({
                type: "line",
                name: ds.label,
                data: ds.data,
                yAxisIndex: yBShown.value ? 1 : 0,
                smooth: false,
                showSymbol: false,
                z: 1,
                lineStyle: {width: props.short ? 0.5 : 1, color: ds.borderColor},
                ...(ds.areaStyle ? {areaStyle: ds.areaStyle} : {}),
            }))

        const axisLabelStyle = {
            color: cssVar("--ks-text-secondary"),
            fontSize: 10,
        }

        const yAxisConfig = (position: "left" | "right", fieldIndex: number) => ({
            type: "value",
            show: showAxes,
            position,
            splitNumber: 5,
            splitLine: {
                show: showAxes && position === "left",
                lineStyle: {type: "dashed", color: cssVar("--ks-border-subtle"), width: 1},
            },
            axisLabel: {
                ...axisLabelStyle,
                ...(position === "left" ? {align: "left"} : {}),
                ...(isDuration(aggregator.value[fieldIndex]?.[1]?.field)
                    ? {formatter: (v: number) => durationUtils.humanDuration(v)}
                    : {}),
            },
        })

        const yAxis = yBShown.value
            ? [yAxisConfig("left", 0), yAxisConfig("right", 1)]
            : yAxisConfig("left", 0)

        return {
            grid: isCompact
                ? {top: 2, right: 2, bottom: 2, left: 2, containLabel: false}
                : {left: 0, right: 0, bottom: "3%", top: "5%", containLabel: true},
            xAxis: {
                type: "category",
                data: xAxisData,
                show: !isCompact,
                axisLine: {lineStyle: {color: cssVar("--ks-border-default")}},
                axisLabel: {...axisLabelStyle, formatter: shortAxisLabel},
            },
            yAxis,
            legend: {
                show: false,
                selected: legendSelected([...barSeries, ...lineSeries].map((s) => s.name)),
            },
            tooltip: {axisPointer: {type: "none"}},
            series: [...barSeries, ...lineSeries],
        }
    })

    const {data: generated, generate} = useChartGenerator(props.dashboardId, props)

    const showLegend = computed(() => !props.short && !props.execution && !!chartOptions?.legend?.enabled)

    const legendStatuses = computed(() =>
        (parsedData.value.datasets as any[])
            .filter((ds) => ds.type !== "line")
            .map((ds) => ({
                label: ds.label as string,
                color: ds.backgroundColor as string,
                count: (ds.data as number[]).reduce((sum, n) => sum + (n || 0), 0),
            })),
    )

    const durationLabel = computed(() =>
        (parsedData.value.datasets as any[]).find((ds) => ds.type === "line")?.label ?? "Duration",
    )

    const ksEchartRef = ref<InstanceType<typeof KsEchart> | null>(null)

    const dimensionColumn = computed(() => {
        const key = (chartOptions as Record<string, any>)?.colorByColumn as string | undefined
        return (key ? data?.columns?.[key] : undefined) as {field?: string; labelKey?: string} | undefined
    })

    function onChartClick(params: any) {
        if (params.seriesType !== "bar" || props.execution) return

        drillDown([
            {column: dimensionColumn.value, value: params.seriesName},
            ...(props.namespace ? [{column: {field: "NAMESPACE"}, value: props.namespace}] : []),
            ...(props.flow ? [{column: {field: "FLOW_ID"}, value: props.flow}] : []),
        ])
    }

    function refresh(customFilters?: FilterObject[]) {
        return generate(undefined, customFilters)
    }

    defineExpose({
        refresh,
    })

    watch(() => route.params.filters, () => refresh(), {deep: true})
</script>

<style scoped lang="scss">
    .chart {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 200px;

        &.short {
            height: 40px;
            min-height: 0;
        }

        &.execution {
            height: 120px;
            min-height: 0;
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
