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
    import {computed, onMounted, ref} from "vue"
    import {use} from "echarts/core"
    import {GraphChart} from "echarts/charts"
    import type {ECharts} from "echarts/core"
    import KsEchart from "./KsEchart.vue"
    import {deepMerge, ChartRenderer} from "./ksChartUtils"

    use([GraphChart])

    defineOptions({inheritAttrs: false})

    // ─── Types ────────────────────────────────────────────────────────────────

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

    // ─── Emits ────────────────────────────────────────────────────────────────

    const emit = defineEmits<{
        "node-click": [node: KsGraphNode]
        "node-hover": [node: KsGraphNode | null]
    }>()

    // ─── Props ────────────────────────────────────────────────────────────────

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

    // ─── Computed ─────────────────────────────────────────────────────────────

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
                    // TODO: probably removed 
                    // The global theme sets animation:false for chart
                    // types that don't need it. Force layout requires
                    // animation to drive its simulation loop, so we
                    // re-enable it here and disable layoutAnimation to
                    // keep the visual result instant (no transition).
                    // animation: true,
                    // animationDuration: 0,
                    // animationDurationUpdate: 0,
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
            // Add any extra override series beyond base length
            mergedSeries.push(...overrideSeries.slice(baseSeries.length))
            const {series: _, ...restOverrides} = overrides
            return {...deepMerge(base, restOverrides), series: mergedSeries}
        }
        return deepMerge(base, overrides)
    })

    // ─── ECharts instance & event wiring ─────────────────────────────────────

    const ksEchartRef = ref<InstanceType<typeof KsEchart> | null>(null)

    onMounted(() => {
        const chart = ksEchartRef.value?.getEchartsInstance() as ECharts | null
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

    // ─── Expose ───────────────────────────────────────────────────────────────

    defineExpose({
        getEchartsInstance: (): ECharts | null =>
            (ksEchartRef.value?.getEchartsInstance() as ECharts) ?? null,

        zoomIn() {
            const chart = ksEchartRef.value?.getEchartsInstance() as ECharts | null
            if (!chart) return
            const option = chart.getOption() as Record<string, unknown>
            const series = option?.series as Record<string, unknown>[]
            const currentZoom = (series?.[0]?.zoom as number) ?? 1
            chart.setOption({series: [{zoom: currentZoom * 1.2}]})
        },

        zoomOut() {
            const chart = ksEchartRef.value?.getEchartsInstance() as ECharts | null
            if (!chart) return
            const option = chart.getOption() as Record<string, unknown>
            const series = option?.series as Record<string, unknown>[]
            const currentZoom = (series?.[0]?.zoom as number) ?? 1
            chart.setOption({series: [{zoom: Math.max(0.1, currentZoom / 1.2)}]})
        },

        fit() {
            const chart = ksEchartRef.value?.getEchartsInstance() as ECharts | null
            if (!chart) return
            chart.setOption({series: [{zoom: 1, center: ["50%", "50%"]}]})
        },

        exportAsImage: (type: "jpeg" | "png", filename?: string) =>
            ksEchartRef.value?.exportAsImage(type, filename),
    })
</script>
