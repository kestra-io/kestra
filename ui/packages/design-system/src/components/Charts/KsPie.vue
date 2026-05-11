<template>
    <KsEchart
        ref="ksEchartRef"
        v-bind="$attrs"
        class="ks-chart--pie"
        :options="mergedOption"
        :loading="isLoading"
        :tooltipType="tooltipType"
        :disableFeatures="disableFeatures"
        :renderer="renderer"
        type="pie"
        @echarts-mouseover="emit('echarts-mouseover', $event)"
        @echarts-mouseout="emit('echarts-mouseout', $event)"
    />
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"

    import {use} from "echarts/core"
    import {PieChart} from "echarts/charts"

    import KsEchart from "./KsEchart.vue"
    import type {KsChartSeriesItem} from "./KsEchart.vue"
    import {deepMerge, ChartFeature, TooltipType, ChartRenderer} from "./ksChartUtils"

    use([PieChart])

    defineOptions({inheritAttrs: false})

    const emit = defineEmits<{
        "echarts-mouseover": [params: unknown]
        "echarts-mouseout": [params: unknown]
    }>()

    const props = withDefaults(
        defineProps<{
            /** Pie slice data loaded asynchronously. Pass `null` or omit while fetching. */
            data?: KsChartSeriesItem[] | null
            /** Partial ECharts option deep-merged over component defaults. */
            options?: Record<string, unknown>
            /** Show the loading spinner. Defaults to `true` when data is null/undefined. */
            loading?: boolean
            /** Render as a donut ring when `true`. */
            donut?: boolean
            /** Tooltip rendering mode: NATIVE uses ECharts built-in tooltip, EXTERNAL uses KsTooltip. */
            tooltipType?: TooltipType
            /** Features to disable (LEGEND, AXIS, AXIS_SPLITLINE, TOOLTIP). */
            disableFeatures?: ChartFeature[]
            /** ECharts renderer backend. */
            renderer?: ChartRenderer
        }>(),
        {
            data: null,
            options: () => ({}),
            loading: undefined,
            donut: false,
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
        const legendHidden = (props.options.legend as {show?: boolean})?.show === false
            || props.disableFeatures.includes(ChartFeature.LEGEND)
        const base: Record<string, unknown> = {
            tooltip: {trigger: "item", formatter: "{b}: {c} ({d}%)"},
            legend: {orient: "vertical", right: "5%", top: "center"},
            series: [
                {
                    type: "pie",
                    radius: props.donut ? ["40%", "70%"] : "60%",
                    center: legendHidden ? ["50%", "50%"] : ["40%", "50%"],
                    data: props.data ?? [],
                    emphasis: {scale: false},
                },
            ],
        }

        return deepMerge(base, props.options ?? {})
    })

    defineExpose({
        getEchartsInstance: () => ksEchartRef.value?.getEchartsInstance() ?? null,
    })
</script>
