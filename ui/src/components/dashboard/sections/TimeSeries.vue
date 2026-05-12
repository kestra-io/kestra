<template>
    <div
        v-if="generated?.total > 0"
        :class="[props.short ? 'short-chart' : props.execution ? 'execution-chart' : 'chart', (!props.short && !props.execution && chartOptions?.legend?.enabled) ? 'with-legend' : '']"
    >
        <KsEchart
            ref="ksEchartRef"
            :options="echartsOption"
            :loading="false"
            :disableFeatures="[ChartFeature.AXIS_SPLITLINE]"
            :tooltipType="TooltipType.EXTERNAL"
        />
    </div>
    <KsEmpty v-else-if="!props.short || (props.execution && generated?.total === 0)" />
</template>

<script setup lang="ts">
    import {computed, ref, watch, PropType} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import moment from "moment"
    import * as echarts from "echarts/core"
    import {use} from "echarts/core"
    import {BarChart, LineChart} from "echarts/charts"
    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {extractState, getConsistentHEXColor} from "../composables/charts"
    import * as KestraUtils from "../../../utils/utils"
    import {useTheme} from "../../../utils/utils"
    import {FilterObject} from "../../../utils/filters"
    import {KsEchart, cssVar, durationUtils} from "@kestra-io/design-system"
    import {TooltipType, ChartFeature} from "@kestra-io/design-system"
    import {useMiscStore} from "override/stores/misc"
    import {useBreakpoints, breakpointsElement} from "@vueuse/core"

    use([BarChart, LineChart])

    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")

    const route = useRoute()
    const router = useRouter()

    defineOptions({inheritAttrs: false})
    const props = defineProps({
        dashboardId: {type: String, required: false, default: undefined},
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
        short: {type: Boolean, default: false},
        execution: {type: Boolean, default: false},
        flow: {type: String, default: undefined},
        namespace: {type: String, default: undefined},
    })

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

    function isDuration(field: string | undefined): boolean {
        return field === "DURATION"
    }

    const parseValue = (value: unknown): unknown => {
        const date = moment(value as moment.MomentInput, moment.ISO_8601, true)
        const query = {
            ...Object.fromEntries(props.filters.map(({field, value: filterValue, operation}) => [`filters[${field}][${operation}]`, filterValue])),
            ...route.query,
        }
        return date.isValid() ? date.format(KestraUtils.getDateFormat(
            (route.query.startDate ?? query["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"]) as string | undefined,
            (route.query.endDate ?? query["filters[endDate][LESS_THAN_OR_EQUAL_TO]"]) as string | undefined,
            query["filters[timeRange][EQUALS]"] as string | undefined,
        )) : value
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
                        borderColor: cssVar("--ks-gray-100"),
                        smooth: true,
                        areaStyle: {
                            opacity: 0.2,
                            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                                {
                                    offset: 0,
                                    color: cssVar("--ks-gray-100"),
                                },
                                {
                                    offset: 1,
                                    color: cssVar("--ks-gray-900"),
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

        const barSeries = (pd.datasets as any[])
            .filter((ds) => ds.type !== "line")
            .map((ds) => ({
                type: "bar",
                name: ds.label,
                data: ds.data,
                stack: "total",
                yAxisIndex: 0,
                itemStyle: {color: ds.backgroundColor},
                barMaxWidth: props.short ? 8 : props.execution ? 24 : 48,
                ...(props.short ? {barCategoryGap: "0%"} : {}),
            }))

        const lineSeries = (pd.datasets as any[])
            .filter((ds) => ds.type === "line")
            .map((ds) => ({
                type: "line",
                name: ds.label,
                data: ds.data,
                yAxisIndex: yBShown.value ? 1 : 0,
                smooth: true,
                showSymbol: false,
                z: 1,
                lineStyle: {width: props.short ? 0.5 : 1, color: ds.borderColor},
                ...(ds.areaStyle ? {areaStyle: ds.areaStyle} : {}),
            }))

        const yAxisConfig = (position: "left" | "right", fieldIndex: number) => ({
            type: "value",
            show: showAxes,
            position,
            axisLabel: isDuration(aggregator.value[fieldIndex]?.[1]?.field)
                ? {formatter: (v: number) => durationUtils.humanDuration(v)}
                : {},
        })

        const yAxis = yBShown.value
            ? [yAxisConfig("left", 0), yAxisConfig("right", 1)]
            : yAxisConfig("left", 0)

        return {
            grid: isCompact
                ? {top: 2, right: 2, bottom: 2, left: 2, containLabel: false}
                : {left: "3%", right: "4%", bottom: "3%", top: chartOptions?.legend?.enabled ? "60px" : "5%", containLabel: true},
            xAxis: {
                type: "category",
                data: xAxisData,
                show: !isCompact,
            },
            yAxis,
            legend: (!isCompact && chartOptions?.legend?.enabled) ? {
                "top": "10px",
                "right": "10px",
            } : {show: false},
            series: [...barSeries, ...lineSeries],
        }
    })

    const {data: generated, generate} = useChartGenerator(props.dashboardId, props)

    const ksEchartRef = ref<InstanceType<typeof KsEchart> | null>(null)

    // Register click handler when the chart mounts (after data loads and v-if becomes true)
    watch(ksEchartRef, (newRef) => {
        if (!newRef) return
        const instance = newRef.getEchartsInstance()
        if (!instance) return
        instance.on("click", (params: any) => {
            if (data?.type === "io.kestra.plugin.core.dashboard.data.Logs" || props.execution) return

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
                    ...(props.namespace ? {"filters[namespace][IN]": props.namespace} : {}),
                    ...(props.flow ? {"filters[flowId][EQUALS]": props.flow} : {}),
                    "filters[timeRange][EQUALS]": useMiscStore()?.configs?.chartDefaultDuration ?? "PT24H",
                },
            })
        })
    })

    function refresh(customFilters?: FilterObject[]) {
        return generate(undefined, customFilters)
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

    .execution-chart {
        height: 120px;
    }
</style>
