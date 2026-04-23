<template>
    <KsEchart
        ref="ksEchartRef"
        class="ks-chart--bar"
        v-bind="$attrs"
        :options="mergedOption"
        :loading="isLoading"
        :tooltipType="tooltipType"
        :disableFeatures="disableFeatures"
        :renderer="renderer"
        :data="data"
        @echarts-mouseover="emit('echarts-mouseover', $event)"
        @echarts-mouseout="emit('echarts-mouseout', $event)"
    />
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"

    import {use} from "echarts/core"
    import {BarChart} from "echarts/charts"

    import KsEchart from "./KsEchart.vue"
    import type {KsChartSeriesItem} from "./KsEchart.vue"
    import {deepMerge, ChartFeature, TooltipType, ChartRenderer} from "./ksChartUtils"

    use([BarChart])

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
            /** Stack bars when `true`. */
            stack?: boolean
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
            stack: false,
            disableFeatures: () => [],
            tooltipType: TooltipType.NATIVE,
            renderer: ChartRenderer.CANVAS,
        },
    )

    const ksEchartRef = ref<InstanceType<typeof KsEchart> | null>(null)

    const isLoading = computed(() => {
        if (props.loading !== undefined) return props.loading
        return props.data === null || props.data === undefined
    })

    const mergedOption = computed(() => {
        const base: Record<string, unknown> = {
            grid: {left: "3%", right: "4%", bottom: "3%", containLabel: true},
            xAxis: {type: "category", data: props.categories},
            yAxis: {type: "value"},
            tooltip: {trigger: "axis", axisPointer: {type: "shadow"}},
            legend: {},
            series: (props.data ?? []).map((s) => ({
                type: "bar",
                ...(props.stack ? {stack: "total"} : {}),
                ...s,
            })),
        }

        return deepMerge(base, props.options ?? {})
    })

    defineExpose({
        getEchartsInstance: () => ksEchartRef.value?.getEchartsInstance() ?? null,
    })
</script>
