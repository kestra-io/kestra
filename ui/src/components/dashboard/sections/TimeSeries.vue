<template>
    <KsSkeleton v-if="loading && !generated && !props.short" animated :rows="3" class="empty" />

    <div
        v-else-if="generated?.total > 0"
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

        <div
            ref="chartWrapper"
            class="canvas-wrapper"
            :class="{'canvas-wrapper--selectable': isSelectable}"
        >
            <KsEchart
                ref="ksEchartRef"
                :maxPixelRatio="DASHBOARD_CHART_MAX_PIXEL_RATIO"
                class="canvas"
                :options="echartsOption"
                :loading="false"
                :tooltipType="TooltipType.EXTERNAL"
                :stickyTooltip="props.short"
                @echarts-click="onChartClick"
            />

            <template v-if="isSelectable">
                <div
                    v-if="brushState.active"
                    class="brush-scrim brush-scrim--left"
                    :style="scrimLeftStyle"
                />
                <div
                    v-if="brushState.active"
                    class="brush-scrim brush-scrim--right"
                    :style="scrimRightStyle"
                />

                <Motion
                    v-if="brushState.active"
                    as="div"
                    class="brush-band"
                    :layout="true"
                    :style="bandStyle"
                    :initial="{opacity: 0, scaleX: 0.8}"
                    :animate="{opacity: 1, scaleX: 1}"
                    :exit="{opacity: 0, scaleX: 0.8}"
                    :transition="{duration: 0.15, ease: 'easeOut'}"
                >
                    <div
                        class="brush-edge brush-edge--left"
                        @mousedown.stop="startEdgeDrag('left', $event)"
                    />
                    <div
                        class="brush-label"
                        @mousedown.stop="startBandDrag($event)"
                    >
                        <span v-if="brushLabel">{{ brushLabel }}</span>
                    </div>
                    <div
                        class="brush-edge brush-edge--right"
                        @mousedown.stop="startEdgeDrag('right', $event)"
                    />
                </Motion>

                <div
                    v-if="isDragging"
                    class="brush-drag-overlay"
                    @mousemove="onDragMove"
                    @mouseup="onDragEnd"
                    @mouseleave="onDragEnd"
                />
            </template>
        </div>
    </div>
    <KsNoData
        v-else-if="!props.short || (props.execution && generated?.total === 0)"
        :class="{empty: !props.short && !props.execution}"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch, onMounted, onUnmounted, nextTick} from "vue"
    import {useRoute} from "vue-router"

    import moment from "moment"
    import {use, graphic} from "echarts/core"
    import {BarChart, LineChart} from "echarts/charts"
    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    import {KsEchart, KsSkeleton, TooltipType, cssVar, durationUtils} from "@kestra-io/design-system"
    import {Motion} from "motion-v"
    import momentTz from "moment-timezone"

    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {DASHBOARD_CHART_MAX_PIXEL_RATIO, fillTimeBucketLabels, getConsistentHEXColor, useLegendToggle} from "../composables/charts"
    import {useChartDrillDown} from "../composables/chartDrillDown"
    import ChartLegend from "./ChartLegend.vue"
    import {getDateGrouping, useTheme} from "../../../utils/utils"
    import {QueryFilter} from "@kestra-io/kestra-sdk"
    import {storageKeys} from "../../../utils/constants"
    import {
        bucketLabelToDateRange,
        pixelSelectionToBucketIndices,
    } from "../../../utils/logsBrushMappers"

    use([BarChart, LineChart])

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        dashboardId?: string;
        chart: Chart;
        filters?: QueryFilter[];
        showDefault?: boolean;
        short?: boolean;
        execution?: boolean;
        flow?: string;
        namespace?: string;
        selectable?: boolean;
        brushStart?: string;
        brushEnd?: string;
    }>(), {
        dashboardId: undefined,
        filters: () => [],
        showDefault: false,
        short: false,
        execution: false,
        flow: undefined,
        namespace: undefined,
        selectable: false,
        brushStart: undefined,
        brushEnd: undefined,
    })

    const emit = defineEmits<{
        select: [{startDate: string; endDate: string} | null]
    }>()

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

    const grouping = computed(() => {
        const query = {
            ...Object.fromEntries(
                props.filters.map(({field, value: filterValue, operation}) =>
                    [`filters[${field}][${operation}]`, filterValue]),
            ),
            ...route.query,
        }
        return getDateGrouping(
            (route.query.startDate ?? query["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"]) as string | undefined,
            (route.query.endDate ?? query["filters[endDate][LESS_THAN_OR_EQUAL_TO]"]) as string | undefined,
            query["filters[timeRange][EQUALS]"] as string | undefined,
        )
    })

    const parseValue = (value: unknown): unknown => {
        const date = moment(value as moment.MomentInput, moment.ISO_8601, true)
        return date.isValid() ? date.format(grouping.value.format) : value
    }

    const shortAxisLabel = (value: string): string => {
        if (typeof value !== "string") return value
        const [datePart, timePart] = value.split(" ")
        if (timePart) return timePart
        const segments = datePart.split("-")
        return segments.length === 3 ? segments.slice(1).join("-") : datePart
    }

    const parsedData = computed(() => {
        const rawData = generated.value.results as Record<string, any>[] | undefined
        // fill the buckets between the earliest and latest returned dates so gaps stay visible on the axis
        const xAxis = fillTimeBucketLabels(
            rawData?.map((v: Record<string, any>) => v[chartOptions?.column ?? ""]) ?? [],
            grouping.value,
        )

        const aggregatorKeys = aggregator.value.map(([key]) => key)

        const reducer = (array: Record<string, any>[] | undefined, field: string, yAxisID: string) => {
            if (!array?.length) return

            const columns = data?.columns ?? {}
            const column = chartOptions?.column ?? ""
            const colorByColumn = (chartOptions as Record<string, any>)?.colorByColumn as string | undefined

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

                if (!current.unique.has(parsedDate)) {
                    current.unique.add(parsedDate)
                    current.data.push({
                        x: parsedDate,
                        y: params[field],
                    })
                } else {
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

        const yDatasetData = Object.values(getData(aggregator.value[0][0], yDataset)).sort((a: any, b: any) =>
            (a.label ?? "").localeCompare(b.label ?? ""),
        )

        const label = aggregator.value?.[1]?.[1]?.displayName ?? aggregator.value?.[1]?.[1]?.field

        let duration: number[] = []
        if(yBShown.value){
            const column = chartOptions?.column ?? ""
            const durationKey = aggregator.value[1][0]

            const groupedDurations: Record<string, number> = {}
            rawData?.forEach((item: Record<string, any>) => {
                const formattedDate = parseValue(item[column]) as string
                groupedDurations[formattedDate] = (groupedDurations[formattedDate] || 0) + item[durationKey]
            })

            // Step 2: Map onto the x-axis labels so the line stays aligned with the bars
            duration = xAxis.map(date => groupedDurations[date] || 0)
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

        // format duration values in the tooltip as human durations instead of raw seconds
        const durationTooltip = (fieldIndex: number) =>
            isDuration(aggregator.value[fieldIndex]?.[1]?.field)
                ? {tooltip: {valueFormatter: (value: unknown) => durationUtils.humanDuration(Number(value))}}
                : {}

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
            ...durationTooltip(0),
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
                ...durationTooltip(yBShown.value ? 1 : 0),
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
                ? {top: 2, right: 2, bottom: 2, left: 2, outerBoundsMode: "none"}
                : {left: 0, right: 0, bottom: "3%", top: "5%", outerBoundsMode: "same"},
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

    const {data: generated, loading, generate} = useChartGenerator(props.dashboardId, props)

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
    const chartWrapper = ref<HTMLElement | null>(null)

    const dimensionColumn = computed(() => {
        const key = (chartOptions as Record<string, any>)?.colorByColumn as string | undefined
        return (key ? data?.columns?.[key] : undefined) as {field?: string; key?: string} | undefined
    })

    function onChartClick(params: any) {
        if (params.seriesType !== "bar" || props.execution) return

        drillDown([
            {column: dimensionColumn.value, value: params.seriesName},
            ...(props.namespace ? [{column: {field: "NAMESPACE"}, value: props.namespace}] : []),
            ...(props.flow ? [{column: {field: "FLOW_ID"}, value: props.flow}] : []),
        ])
    }

    function refresh(customFilters?: QueryFilter[]) {
        return generate(undefined, customFilters)
    }

    defineExpose({
        refresh,
        total: computed(() => generated.value?.total ?? 0),
    })

    watch(() => route.params.filters, () => refresh(), {deep: true})

    // --- Brush ---

    const currentDateFormat = computed(() => grouping.value.format)

    const isSelectable = computed(() =>
        props.selectable &&
        !props.short &&
        !props.execution &&
        !["yyyy-MM", "yyyy-'W'ww"].includes(currentDateFormat.value),
    )

    const userTz = () =>
        localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) ?? momentTz.tz.guess()

    type BrushState = {
        active: boolean;
        startIdx: number;
        endIdx: number;
    }

    const brushState = ref<BrushState>({active: false, startIdx: 0, endIdx: 0})

    const bucketPixels = ref<number[]>([])

    function computeBucketPixels() {
        const chart = ksEchartRef.value?.getEchartsInstance()
        if (!chart) return
        const labels = parsedData.value.labels as string[]
        bucketPixels.value = labels.map((_, i) =>
            (chart.convertToPixel({xAxisIndex: 0}, i) as unknown) as number,
        )
    }

    function idxToPixelCenter(idx: number): number {
        return bucketPixels.value[idx] ?? 0
    }

    function idxToPixelLeft(idx: number): number {
        const px = bucketPixels.value
        if (px.length === 0) return 0
        const step = px.length > 1 ? Math.abs(px[1] - px[0]) / 2 : 0
        return (px[idx] ?? 0) - step
    }

    function idxToPixelRight(idx: number): number {
        const px = bucketPixels.value
        if (px.length === 0) return 0
        const step = px.length > 1 ? Math.abs(px[1] - px[0]) / 2 : 0
        return (px[idx] ?? 0) + step
    }

    const bandStyle = computed(() => {
        if (!brushState.value.active || bucketPixels.value.length === 0) return {}
        const left = idxToPixelLeft(brushState.value.startIdx)
        const right = idxToPixelRight(brushState.value.endIdx)
        return {
            left: `${left}px`,
            width: `${right - left}px`,
        }
    })

    const scrimLeftStyle = computed(() => {
        if (!brushState.value.active || bucketPixels.value.length === 0) return {}
        return {
            left: "0px",
            width: `${idxToPixelLeft(brushState.value.startIdx)}px`,
        }
    })

    const scrimRightStyle = computed(() => {
        if (!brushState.value.active || bucketPixels.value.length === 0) return {}
        const right = idxToPixelRight(brushState.value.endIdx)
        return {
            left: `${right}px`,
            right: "0px",
        }
    })

    const brushLabel = computed(() => {
        if (!brushState.value.active) return ""
        const labels = parsedData.value.labels as string[]
        const fmt = currentDateFormat.value
        const tz = userTz()
        const startRange = bucketLabelToDateRange(labels[brushState.value.startIdx], fmt, tz)
        const endRange = bucketLabelToDateRange(labels[brushState.value.endIdx], fmt, tz)
        if (!startRange || !endRange) return ""
        const formatStr = "YYYY-MM-DD HH:mm"
        const start = momentTz.tz(startRange.startMs, tz).format(formatStr)
        const end = momentTz.tz(endRange.endMs, tz).format(formatStr)
        const durationMs = endRange.endMs - startRange.startMs
        return `${start} → ${end} (${durationUtils.humanDuration(durationMs)})`
    })

    type DragMode = "create" | "band" | "left-edge" | "right-edge"

    const isDragging = ref(false)
    const dragMode = ref<DragMode>("create")
    const dragStartX = ref(0)
    const dragCurrentX = ref(0)
    const dragOriginalStart = ref(0)
    const dragOriginalEnd = ref(0)

    function getChartLocalX(e: MouseEvent): number {
        const rect = chartWrapper.value?.getBoundingClientRect()
        if (!rect) return 0
        return e.clientX - rect.left
    }

    function snapToIndices(xStart: number, xEnd: number): {start: number; end: number} {
        const result = pixelSelectionToBucketIndices(xStart, xEnd, bucketPixels.value)
        if (!result) return {start: 0, end: 0}
        return result
    }

    function commitBrush() {
        if (!brushState.value.active) {
            emit("select", null)
            return
        }
        const labels = parsedData.value.labels as string[]
        const fmt = currentDateFormat.value
        const tz = userTz()
        const startRange = bucketLabelToDateRange(labels[brushState.value.startIdx], fmt, tz)
        const endRange = bucketLabelToDateRange(labels[brushState.value.endIdx], fmt, tz)
        if (!startRange || !endRange) return
        emit("select", {
            startDate: new Date(startRange.startMs).toISOString(),
            endDate: new Date(endRange.endMs).toISOString(),
        })
    }

    function clearBrush() {
        brushState.value = {active: false, startIdx: 0, endIdx: 0}
        emit("select", null)
    }

    function startCreateDrag(localX: number) {
        computeBucketPixels()
        const idx = snapToIndices(localX, localX)
        brushState.value = {active: true, startIdx: idx.start, endIdx: idx.end}
        dragMode.value = "create"
        dragStartX.value = localX
        dragCurrentX.value = localX
        isDragging.value = true
    }

    function startEdgeDrag(edge: "left" | "right", e: MouseEvent) {
        computeBucketPixels()
        dragMode.value = edge === "left" ? "left-edge" : "right-edge"
        dragStartX.value = getChartLocalX(e)
        dragOriginalStart.value = brushState.value.startIdx
        dragOriginalEnd.value = brushState.value.endIdx
        isDragging.value = true
    }

    function startBandDrag(e: MouseEvent) {
        computeBucketPixels()
        dragMode.value = "band"
        dragStartX.value = getChartLocalX(e)
        dragOriginalStart.value = brushState.value.startIdx
        dragOriginalEnd.value = brushState.value.endIdx
        isDragging.value = true
    }

    function onDragMove(e: MouseEvent) {
        if (!isDragging.value) return
        const localX = getChartLocalX(e)
        dragCurrentX.value = localX

        const px = bucketPixels.value
        if (px.length === 0) return

        if (dragMode.value === "create") {
            const snap = snapToIndices(dragStartX.value, localX)
            brushState.value = {active: true, startIdx: snap.start, endIdx: snap.end}
        } else if (dragMode.value === "left-edge") {
            const deltaX = localX - dragStartX.value
            const newLeftPx = idxToPixelCenter(dragOriginalStart.value) + deltaX
            const nearestIdx = pixelSelectionToBucketIndices(newLeftPx, newLeftPx, px)?.start ?? 0
            const rightIdx = brushState.value.endIdx
            if (nearestIdx <= rightIdx) {
                brushState.value = {active: true, startIdx: nearestIdx, endIdx: rightIdx}
            } else {
                brushState.value = {active: true, startIdx: rightIdx, endIdx: nearestIdx}
            }
        } else if (dragMode.value === "right-edge") {
            const deltaX = localX - dragStartX.value
            const newRightPx = idxToPixelCenter(dragOriginalEnd.value) + deltaX
            const nearestIdx = pixelSelectionToBucketIndices(newRightPx, newRightPx, px)?.start ?? px.length - 1
            const leftIdx = brushState.value.startIdx
            if (nearestIdx >= leftIdx) {
                brushState.value = {active: true, startIdx: leftIdx, endIdx: nearestIdx}
            } else {
                brushState.value = {active: true, startIdx: nearestIdx, endIdx: leftIdx}
            }
        } else if (dragMode.value === "band") {
            const deltaX = localX - dragStartX.value
            const origLeftPx = idxToPixelCenter(dragOriginalStart.value)
            const duration = dragOriginalEnd.value - dragOriginalStart.value

            const newLeftIdx = pixelSelectionToBucketIndices(origLeftPx + deltaX, origLeftPx + deltaX, px)?.start ?? 0
            const clampedStart = Math.max(0, Math.min(newLeftIdx, px.length - 1 - duration))
            const clampedEnd = clampedStart + duration
            brushState.value = {active: true, startIdx: clampedStart, endIdx: clampedEnd}
        }
    }

    function onDragEnd(e: MouseEvent) {
        if (!isDragging.value) return

        const localX = getChartLocalX(e)
        const CLICK_THRESHOLD = 4

        if (dragMode.value === "create" && Math.abs(localX - dragStartX.value) < CLICK_THRESHOLD) {
            const snap = snapToIndices(localX, localX)
            brushState.value = {active: true, startIdx: snap.start, endIdx: snap.end}
        }

        isDragging.value = false
        commitBrush()
    }

    let zrMousedownHandler: ((e: any) => void) | null = null
    let zrDblclickHandler: ((e: any) => void) | null = null

    function bindZrEvents() {
        const chart = ksEchartRef.value?.getEchartsInstance()
        if (!chart || !isSelectable.value) return

        const zr = chart.getZr()

        zrMousedownHandler = (e: any) => {
            if (!isSelectable.value) return
            computeBucketPixels()
            const rect = chartWrapper.value?.getBoundingClientRect()
            if (!rect) return
            const localX = e.event.clientX - rect.left
            startCreateDrag(localX)
        }

        zrDblclickHandler = () => {
            clearBrush()
        }

        zr.on("mousedown", zrMousedownHandler)
        zr.on("dblclick", zrDblclickHandler)
    }

    function unbindZrEvents() {
        const chart = ksEchartRef.value?.getEchartsInstance()
        if (!chart) return
        const zr = chart.getZr()
        if (zrMousedownHandler) zr.off("mousedown", zrMousedownHandler)
        if (zrDblclickHandler) zr.off("dblclick", zrDblclickHandler)
    }

    watch(ksEchartRef, () => {
        unbindZrEvents()
        nextTick(bindZrEvents)
    })

    watch(isSelectable, (val) => {
        if (val) nextTick(bindZrEvents)
        else unbindZrEvents()
    })

    watch(() => [props.brushStart, props.brushEnd], ([start, end]) => {
        if (start && end) {
            nextTick(() => {
                computeBucketPixels()
                restoreBrushFromProps()
            })
        } else {
            brushState.value = {active: false, startIdx: 0, endIdx: 0}
        }
    })

    watch(parsedData, () => {
        nextTick(() => {
            computeBucketPixels()
            restoreBrushFromProps()
        })
    })

    function restoreBrushFromProps() {
        if (!props.brushStart || !props.brushEnd || !isSelectable.value) return
        const labels = parsedData.value.labels as string[]
        const fmt = currentDateFormat.value
        const tz = userTz()
        const brushStartMs = new Date(props.brushStart).getTime()
        const brushEndMs = new Date(props.brushEnd).getTime()

        let startIdx = 0
        let endIdx = labels.length - 1

        for (let i = 0; i < labels.length; i++) {
            const range = bucketLabelToDateRange(labels[i], fmt, tz)
            if (!range) continue
            if (range.startMs <= brushStartMs && brushStartMs < range.endMs) startIdx = i
            if (range.startMs < brushEndMs && brushEndMs <= range.endMs) endIdx = i
        }

        if (startIdx <= endIdx) {
            brushState.value = {active: true, startIdx, endIdx}
        }
    }

    function onKeydown(e: KeyboardEvent) {
        if (e.key === "Escape" && brushState.value.active) {
            clearBrush()
        }
    }

    onMounted(() => {
        window.addEventListener("keydown", onKeydown)
        nextTick(bindZrEvents)
    })

    onUnmounted(() => {
        window.removeEventListener("keydown", onKeydown)
        unbindZrEvents()
    })
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

        .canvas-wrapper {
            flex: 1;
            min-height: 0;
            position: relative;

            &--selectable {
                cursor: crosshair;
            }

            .canvas {
                width: 100%;
                height: 100%;
            }
        }
    }

    .empty {
        min-height: 200px;
    }

    .brush-scrim {
        position: absolute;
        top: 0;
        bottom: 0;
        pointer-events: none;
        background: var(--ks-brush-scrim-color, rgba(0, 0, 0, 0.35));
        z-index: 2;
    }

    .brush-band {
        position: absolute;
        top: 0;
        bottom: 0;
        background: var(--ks-brush-band-color, rgba(132, 5, 255, 0.12));
        border: 1px solid var(--ks-brush-band-border, rgba(132, 5, 255, 0.4));
        border-radius: var(--ks-radius-sm);
        z-index: 3;
        display: flex;
        align-items: stretch;
        overflow: visible;
        cursor: move;

        html.dark & {
            --ks-brush-band-color: rgba(132, 5, 255, 0.2);
            --ks-brush-band-border: rgba(132, 5, 255, 0.5);
        }
    }

    .brush-edge {
        width: 8px;
        flex-shrink: 0;
        cursor: ew-resize;
        display: flex;
        align-items: center;
        justify-content: center;

        &::after {
            content: "";
            display: block;
            width: 2px;
            height: 40%;
            border-radius: 1px;
            background: var(--ks-btn-primary-default-background, #8405ff);
            opacity: 0.7;
        }

        &:hover::after {
            opacity: 1;
        }
    }

    .brush-label {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        min-width: 0;
        overflow: hidden;
        cursor: move;

        span {
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-secondary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            padding: 0 var(--ks-spacing-1);
            pointer-events: none;
        }
    }

    .brush-drag-overlay {
        position: fixed;
        inset: 0;
        z-index: 9999;
        cursor: crosshair;
    }
</style>
