<template>
    <KsSkeleton v-if="loading && !generated && !props.short" animated :rows="3" class="empty" />

    <div
        v-else-if="(generated?.total ?? 0) > 0"
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
            data-test="time-series-canvas"
            :class="{'canvas-wrapper--selectable': isSelectable}"
            :tabindex="isSelectable ? -1 : undefined"
            @keydown.esc="onEscape"
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

            <template v-if="isSelectable && brushState.active">
                <div class="brush-scrim" :style="scrimLeftStyle" />
                <div class="brush-scrim" :style="scrimRightStyle" />

                <Motion
                    as="div"
                    class="brush-band"
                    :style="bandStyle"
                    :initial="{opacity: 0}"
                    :animate="{opacity: 1}"
                    :transition="{duration: 0.15, ease: 'easeOut'}"
                >
                    <div
                        class="brush-edge"
                        @mousedown.left.stop="startEdgeDrag('left', $event)"
                    />
                    <div
                        class="brush-label"
                        @mousedown.left.stop="startBandDrag($event)"
                    >
                        <span v-if="brushLabel">{{ brushLabel }}</span>
                    </div>
                    <div
                        class="brush-edge"
                        @mousedown.left.stop="startEdgeDrag('right', $event)"
                    />
                </Motion>
            </template>

            <div
                v-if="isDragging"
                class="brush-drag-overlay"
                :class="{'brush-drag-overlay--resize': dragMode !== 'create' && dragMode !== 'band', 'brush-drag-overlay--move': dragMode === 'band'}"
            />
        </div>
    </div>
    <KsNoData
        v-else-if="!props.short || (props.execution && generated?.total === 0)"
        :class="{empty: !props.short && !props.execution}"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch, onBeforeUnmount} from "vue"
    import {useRoute} from "vue-router"

    import {use, graphic, type ElementEvent} from "echarts/core"
    import {BarChart, LineChart} from "echarts/charts"
    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    import {KsEchart, KsSkeleton, TooltipType, cssVar, dateUtils, dayjs, durationUtils} from "@kestra-io/design-system"
    import {Motion} from "motion-v"

    import {Chart, useChartGenerator} from "../composables/useDashboards"
    import {DASHBOARD_CHART_MAX_PIXEL_RATIO, fillTimeBucketLabels, getConsistentHEXColor, useLegendToggle, type EchartsParams} from "../composables/charts"
    import {useChartDrillDown} from "../composables/chartDrillDown"
    import ChartLegend from "./ChartLegend.vue"
    import {getDateGrouping} from "../../../utils/utils"
    import {useTheme} from "../../../composables/useTheme"
    import {QueryFilter} from "@kestra-io/kestra-sdk"
    import {
        bucketLabelToDateRange,
        isBrushableUnit,
        pixelSelectionToBucketIndices,
        type BrushRange,
        type DateRange,
    } from "../../../utils/logsBrushMappers"

    use([BarChart, LineChart])

    defineOptions({inheritAttrs: false})

    /** Per-stack accumulator built while grouping raw rows into bar series; flattened into `number[]` data before being handed to ECharts. */
    interface BarAccumulatorEntry {
        type: "bar";
        yAxisID: string;
        data: {x: unknown; y: number}[];
        tooltip: string;
        label: string | undefined;
        backgroundColor: string;
        unique: Set<unknown>;
    }

    /** `BarAccumulatorEntry` once `data` has been flattened from `{x,y}[]` onto the shared x-axis as `number[]`. */
    type FlattenedBarDataset = Omit<BarAccumulatorEntry, "data"> & {data: number[]}

    /** The duration line series added alongside the bar datasets when a second (duration) aggregator is present. */
    interface LineDataset {
        type: "line";
        yAxisID: string;
        data: number[];
        label: string | undefined;
        borderColor: string;
        smooth: boolean;
        areaStyle?: unknown;
    }

    type TimeSeriesDataset = FlattenedBarDataset | LineDataset

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
        select: [BrushRange | null]
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
        const date = dateUtils.parseIso(value)
        return date.isValid() ? date.format(grouping.value.format) : value
    }

    const shortAxisLabel = (value: string): string => {
        if (typeof value !== "string") return value
        const [datePart, timePart] = value.split(" ")
        if (timePart) return timePart
        const segments = datePart.split("-")
        return segments.length === 3 ? segments.slice(1).join("-") : datePart
    }

    const parsedData = computed((): {labels: string[]; datasets: TimeSeriesDataset[]} => {
        const rawData = generated.value?.results
        // fill the buckets between the earliest and latest returned dates so gaps stay visible on the axis
        const xAxis = fillTimeBucketLabels(
            rawData?.map((v: Record<string, unknown>) => v[chartOptions?.column ?? ""]) ?? [],
            grouping.value,
        )

        const aggregatorKeys = aggregator.value.map(([key]) => key)

        const reducer = (array: Record<string, unknown>[] | undefined, field: string, yAxisID: string) => {
            if (!array?.length) return

            const columns = data?.columns ?? {}
            const column = chartOptions?.column ?? ""
            const colorByColumn = chartOptions?.colorByColumn

            // Get the fields for stacks (columns without `agg` and not the xAxis column)
            const fields = Object.keys(columns)
                .filter(key => !aggregatorKeys.includes(key))
                .filter(key => key !== column)

            return array.reduce<Record<string, BarAccumulatorEntry>>((acc, {...params}) => {
                const stack = fields.map((f) => params[f]).join(", ")

                if (!acc[stack]) {
                    const stackLabel = colorByColumn ? params[colorByColumn] as string | undefined : undefined
                    acc[stack] = {
                        type: "bar",
                        yAxisID,
                        data: [],
                        tooltip: stack,
                        label: stackLabel,
                        backgroundColor: getConsistentHEXColor(theme.value, stackLabel),
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
                        y: params[field] as number,
                    })
                } else {
                    // Update existing stack value for the same date
                    const existing = current.data.find((v) => v.x === parsedDate)
                    if (existing) existing.y += params[field] as number
                }

                return acc
            }, {})
        }

        const getData = (_field: string, object: Record<string, BarAccumulatorEntry> = {}) => {
            return Object.values(object).map((dataset) => {
                const datasetData = xAxis.map((xAxisLabel) => {
                    const temp = dataset.data.find((v) => v.x === xAxisLabel)
                    return temp ? temp.y : 0
                })

                return {...dataset, data: datasetData}
            })
        }

        const yDataset = reducer(rawData, aggregator.value[0][0], "y")

        // Sorts the dataset array alphabetically by label for a consistent order across time ranges.
        const yDatasetData = Object.values(getData(aggregator.value[0][0], yDataset)).sort((a, b) =>
            (a.label ?? "").localeCompare(b.label ?? ""),
        )

        const label = aggregator.value?.[1]?.[1]?.displayName ?? aggregator.value?.[1]?.[1]?.field

        let duration: number[] = []
        if(yBShown.value){
            const column = chartOptions?.column ?? ""
            const durationKey = aggregator.value[1][0]

            // Step 1: Group durations by formatted date
            const groupedDurations: Record<string, number> = {}
            rawData?.forEach((item: Record<string, unknown>) => {
                const formattedDate = parseValue(item[column]) as string
                groupedDurations[formattedDate] = (groupedDurations[formattedDate] || 0) + (item[durationKey] as number)
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
        const xAxisData = pd.labels
        const isCompact = props.short || props.execution
        const showAxes = !isCompact && !verticalLayout.value

        const barDatasets = pd.datasets.filter((ds): ds is FlattenedBarDataset => ds.type !== "line")
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
            data: ds.data.map((value, x) => ({
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

        const lineSeries = pd.datasets
            .filter((ds): ds is LineDataset => ds.type === "line")
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
        parsedData.value.datasets
            .filter((ds): ds is FlattenedBarDataset => ds.type !== "line")
            .map((ds) => ({
                label: ds.label ?? "",
                color: ds.backgroundColor,
                count: ds.data.reduce((sum, n) => sum + (n || 0), 0),
            })),
    )

    const durationLabel = computed(() =>
        parsedData.value.datasets.find((ds): ds is LineDataset => ds.type === "line")?.label ?? "Duration",
    )

    const ksEchartRef = ref<InstanceType<typeof KsEchart> | null>(null)
    const chartWrapper = ref<HTMLElement | null>(null)

    const dimensionColumn = computed(() => {
        const key = chartOptions?.colorByColumn
        return key ? data?.columns?.[key] : undefined
    })

    // The row's date is already the boundary the backend truncated to: re-snapping it with `startOf`
    // would re-anchor the window to the browser's calendar and to dayjs's Sunday-based week.
    function bucketDateRange(dataIndex: number): {startDate: string; endDate: string} | undefined {
        const label = parsedData.value.labels[dataIndex]
        const column = chartOptions?.column ?? ""

        const dates = generated.value?.results
            ?.map((row) => dateUtils.parseIso(row[column]))
            .filter((date) => date.isValid() && date.format(grouping.value.format) === label) ?? []
        if (!dates.length) return undefined

        const bucket = dayjs.min(dates)!

        return {
            startDate: bucket.toISOString(),
            endDate: bucket.add(1, grouping.value.unit).subtract(1, "millisecond").toISOString(),
        }
    }

    function onChartClick(rawParams: unknown) {
        const params = rawParams as EchartsParams
        if (params.seriesType !== "bar" || props.execution || isSelectable.value) return

        drillDown(
            [
                {column: dimensionColumn.value, value: params.seriesName ?? ""},
                ...(props.namespace ? [{column: {field: "NAMESPACE"}, value: props.namespace}] : []),
                ...(props.flow ? [{column: {field: "FLOW_ID"}, value: props.flow}] : []),
            ],
            {dateRange: params.dataIndex === undefined ? undefined : bucketDateRange(params.dataIndex)},
        )
    }

    function refresh(customFilters?: QueryFilter[]) {
        return generate(undefined, customFilters)
    }

    defineExpose({
        refresh,
        total: computed(() => generated.value?.total ?? 0),
    })

    watch(() => route.fullPath, () => refresh())

    const labelTimezone = dayjs.tz.guess()

    const isSelectable = computed(() =>
        props.selectable &&
        !props.short &&
        !props.execution &&
        isBrushableUnit(grouping.value.unit),
    )

    const bucketRanges = computed(() =>
        parsedData.value.labels.map((label) => bucketLabelToDateRange(label, grouping.value.unit, labelTimezone)),
    )

    type BrushState = {
        active: boolean;
        startIdx: number;
        endIdx: number;
    }

    const INACTIVE_BRUSH: BrushState = {active: false, startIdx: 0, endIdx: 0}

    const brushState = ref<BrushState>({...INACTIVE_BRUSH})

    const bucketPixels = ref<number[]>([])
    const chartWidth = ref(0)

    function computeBucketPixels() {
        const chart = ksEchartRef.value?.getEchartsInstance()
        if (!chart) return
        const pixels = parsedData.value.labels.map((_, i) =>
            (chart.convertToPixel({xAxisIndex: 0}, i) as unknown) as number,
        )
        if (pixels.join() !== bucketPixels.value.join()) bucketPixels.value = pixels
        chartWidth.value = chart.getWidth()
    }

    function idxToPixelCenter(idx: number): number {
        return bucketPixels.value[idx] ?? 0
    }

    function halfBucketWidth(): number {
        const px = bucketPixels.value
        if (px.length > 1) return Math.abs(px[1] - px[0]) / 2
        return px.length === 1 ? Math.max(0, chartWidth.value - px[0]) : 0
    }

    function idxToPixelLeft(idx: number): number {
        return idxToPixelCenter(idx) - halfBucketWidth()
    }

    function idxToPixelRight(idx: number): number {
        return idxToPixelCenter(idx) + halfBucketWidth()
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
        return {
            left: `${idxToPixelRight(brushState.value.endIdx)}px`,
            right: "0px",
        }
    })

    function selectedRange(): DateRange | undefined {
        const start = bucketRanges.value[brushState.value.startIdx]
        const end = bucketRanges.value[brushState.value.endIdx]
        return start && end ? {startMs: start.startMs, endMs: end.endMs} : undefined
    }

    const brushLabel = computed(() => {
        const range = brushState.value.active ? selectedRange() : undefined
        if (!range) return ""
        const start = dateUtils.dateFilter(range.startMs)
        const end = dateUtils.dateFilter(range.endMs)
        return `${start} → ${end} (${durationUtils.humanDuration((range.endMs - range.startMs) / 1000)})`
    })

    type DragMode = "create" | "band" | "left-edge" | "right-edge"

    const isDragging = ref(false)
    const dragMode = ref<DragMode>("create")
    const dragStartX = ref(0)
    const dragOriginalStart = ref(0)
    const dragOriginalEnd = ref(0)

    function getChartLocalX(e: MouseEvent): number {
        const rect = chartWrapper.value?.getBoundingClientRect()
        if (!rect) return 0
        return e.clientX - rect.left
    }

    function snapToIndices(xStart: number, xEnd: number): {start: number; end: number} {
        return pixelSelectionToBucketIndices(xStart, xEnd, bucketPixels.value) ?? {start: 0, end: 0}
    }

    function commitBrush() {
        const range = brushState.value.active ? selectedRange() : undefined
        if (!range) return
        const startDate = new Date(range.startMs).toISOString()
        const endDate = new Date(range.endMs - 1).toISOString()
        if (startDate === props.brushStart && endDate === props.brushEnd) return
        emit("select", {startDate, endDate})
    }

    function stopDragging() {
        isDragging.value = false
        window.removeEventListener("mousemove", onDragMove)
        window.removeEventListener("mouseup", onDragEnd)
    }

    function clearBrush() {
        stopDragging()
        brushState.value = {...INACTIVE_BRUSH}
        if (props.brushStart || props.brushEnd) emit("select", null)
    }

    function cancelDrag() {
        stopDragging()
        restoreBrushFromProps()
    }

    function onEscape() {
        if (isDragging.value) cancelDrag()
        else if (brushState.value.active) clearBrush()
    }

    function startDrag(mode: DragMode, e: MouseEvent): boolean {
        if (e.detail > 1) {
            clearBrush()
            return false
        }
        computeBucketPixels()
        chartWrapper.value?.focus({preventScroll: true})
        dragMode.value = mode
        dragStartX.value = getChartLocalX(e)
        dragOriginalStart.value = brushState.value.startIdx
        dragOriginalEnd.value = brushState.value.endIdx
        isDragging.value = true
        window.addEventListener("mousemove", onDragMove)
        window.addEventListener("mouseup", onDragEnd)
        return true
    }

    function startCreateDrag(e: MouseEvent) {
        if (!startDrag("create", e)) return
        const idx = snapToIndices(dragStartX.value, dragStartX.value)
        brushState.value = {active: true, startIdx: idx.start, endIdx: idx.end}
    }

    function startEdgeDrag(edge: "left" | "right", e: MouseEvent) {
        startDrag(edge === "left" ? "left-edge" : "right-edge", e)
    }

    function startBandDrag(e: MouseEvent) {
        startDrag("band", e)
    }

    function nearestIdx(px: number): number {
        return snapToIndices(px, px).start
    }

    function onDragMove(e: MouseEvent) {
        if (!isDragging.value) return
        const localX = getChartLocalX(e)
        const lastIdx = bucketPixels.value.length - 1
        if (lastIdx < 0) return

        const deltaX = localX - dragStartX.value

        if (dragMode.value === "create") {
            const snap = snapToIndices(dragStartX.value, localX)
            brushState.value = {active: true, startIdx: snap.start, endIdx: snap.end}
        } else if (dragMode.value === "left-edge" || dragMode.value === "right-edge") {
            const isLeft = dragMode.value === "left-edge"
            const moved = nearestIdx(idxToPixelCenter(isLeft ? dragOriginalStart.value : dragOriginalEnd.value) + deltaX)
            const pinned = isLeft ? dragOriginalEnd.value : dragOriginalStart.value
            brushState.value = {active: true, startIdx: Math.min(moved, pinned), endIdx: Math.max(moved, pinned)}
        } else {
            const span = dragOriginalEnd.value - dragOriginalStart.value
            const movedStart = nearestIdx(idxToPixelCenter(dragOriginalStart.value) + deltaX)
            const startIdx = Math.max(0, Math.min(movedStart, lastIdx - span))
            brushState.value = {active: true, startIdx, endIdx: startIdx + span}
        }
    }

    function onDragEnd() {
        if (!isDragging.value) return
        stopDragging()
        commitBrush()
    }

    function onZrMousedown(e: ElementEvent) {
        const event = e.event as MouseEvent
        if (!isSelectable.value || event.button !== 0) return
        startCreateDrag(event)
    }

    watch(() => ksEchartRef.value?.getEchartsInstance() ?? null, (chart, previous) => {
        previous?.getZr().off("mousedown", onZrMousedown)
        previous?.off("finished", computeBucketPixels)
        chart?.getZr().on("mousedown", onZrMousedown)
        chart?.on("finished", computeBucketPixels)
    }, {immediate: true})

    function restoreBrushFromProps() {
        if (!props.brushStart || !props.brushEnd || !isSelectable.value) {
            brushState.value = {...INACTIVE_BRUSH}
            return
        }
        const brushStartMs = Date.parse(props.brushStart)
        const brushEndMs = Date.parse(props.brushEnd)
        const overlapping = bucketRanges.value.flatMap((range, i) =>
            range && range.endMs > brushStartMs && range.startMs <= brushEndMs ? [i] : [],
        )

        brushState.value = overlapping.length
            ? {active: true, startIdx: overlapping[0], endIdx: overlapping[overlapping.length - 1]}
            : {...INACTIVE_BRUSH}
    }

    watch([() => props.brushStart, () => props.brushEnd, bucketRanges, isSelectable], () => {
        if (!isDragging.value) restoreBrushFromProps()
    }, {immediate: true})

    onBeforeUnmount(() => {
        stopDragging()
        const chart = ksEchartRef.value?.getEchartsInstance()
        chart?.getZr().off("mousedown", onZrMousedown)
        chart?.off("finished", computeBucketPixels)
    })
</script>

<style scoped>
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

            &:focus {
                outline: none;
            }

            .canvas {
                width: 100%;
                height: 100%;
            }
        }

        & .canvas-wrapper--selectable {
            cursor: crosshair;
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
        background: var(--ks-bg-scrim);
        z-index: 2;
    }

    .brush-band {
        position: absolute;
        top: 0;
        bottom: 0;
        background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 12%, transparent);
        border: var(--ks-border-width-thin) solid color-mix(in srgb, var(--ks-btn-primary-bg-default) 40%, transparent);
        border-radius: var(--ks-radius-sm);
        z-index: 3;
        display: flex;
        align-items: stretch;
        overflow: visible;
        cursor: move;

        html.dark & {
            background: color-mix(in srgb, var(--ks-btn-primary-bg-default) 20%, transparent);
            border-color: color-mix(in srgb, var(--ks-btn-primary-bg-default) 50%, transparent);
        }
    }

    .brush-edge {
        width: var(--ks-spacing-2);
        flex-shrink: 0;
        cursor: ew-resize;
        display: flex;
        align-items: center;
        justify-content: center;

        &::after {
            content: "";
            display: block;
            width: var(--ks-border-width-base);
            height: 40%;
            border-radius: var(--ks-radius-xs);
            background: var(--ks-btn-primary-bg-default);
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

    .brush-drag-overlay--resize {
        cursor: ew-resize;
    }

    .brush-drag-overlay--move {
        cursor: move;
    }
</style>
