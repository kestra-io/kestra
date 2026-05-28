<template>
    <KsEchart
        ref="ksEchartRef"
        class="ks-chart--graph"
        v-bind="$attrs"
        :options="mergedOption"
        :loading="isLoading"
        :renderer="renderer"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watchEffect} from "vue"

    import {use} from "echarts/core"
    import type {ECharts} from "echarts/core"
    import {GraphChart} from "echarts/charts"

    import KsEchart from "./KsEchart.vue"
    import {deepMerge, ChartRenderer} from "./ksChartUtils"

    use([GraphChart])

    defineOptions({inheritAttrs: false})

    export interface KsGraphNode {
        id: string
        name?: string
        symbolSize?: number
        value?: number
        itemStyle?: Record<string, unknown>
        label?: Record<string, unknown>
        [key: string]: unknown
    }

    export interface KsGraphEdge {
        source: string
        target: string
        lineStyle?: Record<string, unknown>
        [key: string]: unknown
    }

    const emit = defineEmits<{
        "node-click": [node: KsGraphNode]
        "node-hover": [node: KsGraphNode | null]
    }>()

    const props = withDefaults(
        defineProps<{
            /** Nodes to render. Pass `null` or omit while fetching to show the loading spinner. */
            nodes?: KsGraphNode[] | null
            /** Edges to render. */
            edges?: KsGraphEdge[] | null
            /** Partial ECharts option deep-merged over component defaults. */
            options?: Record<string, unknown>
            /** Show the loading spinner. Defaults to `true` when nodes is null/undefined. */
            loading?: boolean
            /** Force-directed layout, circular placement, or manual positions. */
            layout?: "force" | "circular" | "none"
            /** Enable pan and zoom: true for both, 'move' for pan only, 'scale' for zoom only. */
            roam?: boolean | "move" | "scale"
            /** ECharts renderer backend. */
            renderer?: ChartRenderer
        }>(),
        {
            nodes: null,
            edges: null,
            options: () => ({}),
            loading: undefined,
            layout: "force",
            roam: true,
            renderer: ChartRenderer.CANVAS,
        },
    )

    const isLoading = computed(() => {
        if (props.loading !== undefined) return props.loading
        return props.nodes === null || props.nodes === undefined
    })

    const mergedOption = computed(() => {
        const base: Record<string, unknown> = {
            series: [
                {
                    type: "graph",
                    layout: props.layout,
                    data: props.nodes ?? [],
                    links: props.edges ?? [],
                    roam: props.roam,
                    edgeSymbol: ["none", "arrow"],
                    emphasis: {
                        focus: "adjacency",
                    },
                    force: {
                        repulsion: 400,
                        gravity: 0.05,
                        edgeLength: 80,
                        layoutAnimation: false,
                        friction: 0.6,
                    },
                },
            ],
        }
        const overrides = props.options ?? {}
        // Merge series elements individually so partial overrides preserve base fields
        if (Array.isArray(overrides.series) && Array.isArray(base.series)) {
            const baseSeries = base.series as Record<string, unknown>[]
            const overrideSeries = overrides.series as Record<string, unknown>[]
            const mergedSeries = baseSeries.map((item, i) =>
                i < overrideSeries.length ? deepMerge(item, overrideSeries[i]) : item,
            )
            mergedSeries.push(...overrideSeries.slice(baseSeries.length))
            const {series: _, ...restOverrides} = overrides
            return {...deepMerge(base, restOverrides), series: mergedSeries}
        }
        return deepMerge(base, overrides)
    })

    const ksEchartRef = ref<InstanceType<typeof KsEchart> | null>(null)

    const getChart = (): ECharts | null => ksEchartRef.value?.getEchartsInstance() ?? null

    watchEffect(() => {
        const chart = getChart()
        if (!chart) return

        chart.on("click", (params: Record<string, unknown>) => {
            if (params.dataType === "node") {
                emit("node-click", params.data as KsGraphNode)
            }
        })
        chart.on("mouseover", (params: Record<string, unknown>) => {
            if (params.dataType === "node") {
                emit("node-hover", params.data as KsGraphNode)
            }
        })
        chart.on("mouseout", (params: Record<string, unknown>) => {
            if (params.dataType === "node") {
                emit("node-hover", null)
            }
        })
    })

    const currentZoom = (chart: ECharts): number => {
        const option = chart.getOption() as Record<string, unknown>
        const series = option?.series as Record<string, unknown>[]
        return (series?.[0]?.zoom as number) ?? 1
    }

    defineExpose({
        getEchartsInstance: getChart,

        zoomIn() {
            const chart = getChart()
            if (!chart) return
            chart.setOption({series: [{zoom: currentZoom(chart) * 1.2}]})
        },

        zoomOut() {
            const chart = getChart()
            if (!chart) return
            chart.setOption({series: [{zoom: Math.max(0.1, currentZoom(chart) / 1.2)}]})
        },

        fit() {
            const chart = getChart()
            if (!chart) return
            chart.resize()
            chart.setOption({series: [{zoom: 1, center: ["50%", "50%"]}]})
        },

        exportAsImage: (type: "jpeg" | "png", filename?: string) =>
            ksEchartRef.value?.exportAsImage(type, filename),
    })
</script>
