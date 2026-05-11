<template>
    <KsEchart
        ref="ksEchartRef"
        class="ks-chart--line"
        v-bind="$attrs"
        :options="mergedOption"
        :loading="isLoading"
        :tooltipType="tooltipType"
        :disableFeatures="disableFeatures"
        :renderer="renderer"
        type="line"
        :data="data"
        @echarts-mouseover="emit('echarts-mouseover', $event)"
        @echarts-mouseout="emit('echarts-mouseout', $event)"
    />
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"

    import {use} from "echarts/core"
    import {LineChart} from "echarts/charts"

    import KsEchart from "./KsEchart.vue"
    import type {KsChartSeriesItem} from "./KsEchart.vue"
    import {deepMerge, ChartFeature, TooltipType, ChartRenderer} from "./ksChartUtils"

    use([LineChart])

    defineOptions({inheritAttrs: false})

    const emit = defineEmits<{
        "echarts-mouseover": [params: unknown]
        "echarts-mouseout": [params: unknown]
    }>()

    const props = withDefaults(
        defineProps<{
            /** Series data loaded asynchronously. Pass `null` or omit while fetching. */
            data?: KsChartSeriesItem[] | null
            /** X-axis category labels. */
            categories?: string[]
            /** Partial ECharts option deep-merged over component defaults. */
            options?: Record<string, unknown>
            /** Show the loading spinner. Defaults to `true` when data is null/undefined. */
            loading?: boolean
            /** Features to disable (LEGEND, AXIS, AXIS_SPLITLINE, TOOLTIP). */
            disableFeatures?: ChartFeature[]
            /** Tooltip rendering mode: NATIVE uses ECharts built-in tooltip, EXTERNAL uses KsTooltip. */
            tooltipType?: TooltipType
            /** ECharts renderer backend. */
            renderer?: ChartRenderer
        }>(),
        {
            data: null,
            categories: () => [],
            options: () => ({}),
            loading: undefined,
            disableFeatures: () => [],
            tooltipType: TooltipType.NATIVE,
            renderer: ChartRenderer.CANVAS,
        },
    )

    const isLoading = computed(() => {
        if (props.loading !== undefined) return props.loading
        return props.data === null || props.data === undefined
    })

    /**
     * When AXIS is disabled (compact/sparkline mode): suppress symbols and add a subtle area fill.
     */
    const effectiveData = computed(() => {
        if (!props.disableFeatures?.includes(ChartFeature.AXIS) || !props.data) return props.data
        return props.data.map((s) => ({
            ...s,
            symbol: "none",
            areaStyle: (s as Record<string, unknown>).areaStyle ?? {opacity: 0.15},
        }))
    })

    const mergedOption = computed(() => {
        const base: Record<string, unknown> = {
            grid: {left: "3%", right: "4%", bottom: "3%", containLabel: true},
            xAxis: {type: "category", boundaryGap: false, data: props.categories},
            yAxis: {type: "value"},
            tooltip: {trigger: "axis"},
            legend: {},
            series: ((effectiveData.value ?? []) as KsChartSeriesItem[]).map((s) => ({
                type: "line",
                ...s,
            })),
        }

        return deepMerge(base, props.options ?? {})
    })

    const ksEchartRef = ref<InstanceType<typeof KsEchart> | null>(null)

    defineExpose({
        getEchartsInstance: () => ksEchartRef.value?.getEchartsInstance() ?? null,
    })
</script>
