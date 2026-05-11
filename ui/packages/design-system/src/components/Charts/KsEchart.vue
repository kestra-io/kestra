<template>
    <KsTooltip
        v-if="tooltipType === TooltipType.EXTERNAL"
        trigger="manual"
        :visible="tooltipVisible"
        :content="tooltipContent"
        :rawContent="true"
        placement="bottom"
    >
        <div
            ref="wrapperRef"
            v-ks-loading="loading"
            class="ks-chart-wrapper"
            v-bind="$attrs"
            @mouseleave="onMouseleave"
        >
            <VChart
                v-if="canRender"
                ref="vChartRef"
                class="ks-chart__inner"
                :theme="currentTheme"
                :option="effectiveOption"
                :initOptions="{renderer: renderer}"
                autoresize
                @mouseover="emit('echarts-mouseover', $event)"
                @mouseout="emit('echarts-mouseout', $event)"
            />
        </div>
    </KsTooltip>

    <div
        v-else
        ref="wrapperRef"
        v-ks-loading="loading"
        class="ks-chart-wrapper"
        v-bind="$attrs"
    >
        <VChart
            v-if="canRender"
            ref="vChartRef"
            class="ks-chart__inner"
            :theme="currentTheme"
            :option="effectiveOption"
            :initOptions="{renderer: renderer}"
            autoresize
            @mouseover="emit('echarts-mouseover', $event)"
            @mouseout="emit('echarts-mouseout', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted, watch} from "vue"

    import {useElementSize} from "@vueuse/core"
    import VChart from "vue-echarts"
    import {use} from "echarts/core"
    import type {ECharts} from "echarts/core"
    import {CanvasRenderer, SVGRenderer} from "echarts/renderers"
    import {
        GridComponent,
        TooltipComponent,
        LegendComponent,
        DataZoomComponent,
        GraphicComponent,
    } from "echarts/components"

    import {vKsLoading} from "../Feedback/KsLoading"
    import KsTooltip from "../Feedback/KsTooltip.vue"
    import KsTheme from "./ksTheme.ts"
    import {deepMerge, buildDisabledFeaturesOverride, ChartFeature, TooltipType, ChartRenderer} from "./ksChartUtils"

    defineOptions({inheritAttrs: false})

    use([CanvasRenderer, SVGRenderer, GridComponent, GraphicComponent, TooltipComponent, LegendComponent, DataZoomComponent])

    export interface KsChartSeriesItem {
        name?: string
        [key: string]: unknown
    }

    const emit = defineEmits<{
        "echarts-mouseover": [params: unknown]
        "echarts-mouseout": [params: unknown]
    }>()

    const props = withDefaults(
        defineProps<{
            /** Final ECharts option object to render. */
            options: Record<string, unknown>
            /** Show the loading overlay. */
            loading?: boolean
            /** Tooltip rendering mode. EXTERNAL uses KsTooltip (ideal for mini/sparkline charts). */
            tooltipType?: TooltipType
            /** Features to disable (LEGEND, AXIS, AXIS_SPLITLINE, TOOLTIP). */
            disableFeatures?: ChartFeature[]
            /** Raw series data — if not provided as options. */
            data?: KsChartSeriesItem[] | null,
            renderer?: ChartRenderer
        }>(),
        {
            loading: false,
            tooltipType: TooltipType.NATIVE,
            disableFeatures: () => [],
            data: null,
            renderer: ChartRenderer.CANVAS,
        },
    )

    const isDark = ref(false)

    function detectDark() {
        isDark.value = document.documentElement.classList.contains("dark")
    }

    let observer: MutationObserver | null = null

    onMounted(() => {
        detectDark()
        observer = new MutationObserver(detectDark)
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["class"],
        })
    })

    onUnmounted(() => {
        observer?.disconnect()
    })

    const effectiveOption = computed(() => {
        let base = props.options

        if (props.tooltipType === TooltipType.EXTERNAL) {
            const userTooltip = typeof base.tooltip === "object" && base.tooltip !== null ? base.tooltip as Record<string, unknown> : {}
            base = {
                ...base,
                tooltip: {
                    trigger: "axis",
                    ...userTooltip,
                    // Move the native tooltip offscreen so ECharts still computes
                    // the axis-pointer snap and calls our formatter, but nothing
                    // is visible to the user.
                    position: () => [-9999, -9999],
                    formatter: (params: unknown) => {
                        tooltipContent.value = buildContentFromParams(params)
                        tooltipVisible.value = true
                        return " "
                    },
                },
            }
        }

        if (props.disableFeatures && props.disableFeatures.length > 0) {
            base = deepMerge(base, buildDisabledFeaturesOverride(props.disableFeatures, base))
        }

        return base
    })

    const currentTheme = computed(() => {
        void isDark.value // reactive dependency — triggers rebuild on theme change
        return KsTheme()
    })

    const vChartRef = ref<InstanceType<typeof VChart> | null>(null)
    const wrapperRef = ref<HTMLElement | null>(null)
    const tooltipVisible = ref(false)
    const tooltipContent = ref("")

    // Defer mounting VChart until the wrapper has real dimensions. ECharts
    // emits "Can't get DOM width or height" if it initializes inside a 0×0
    // container — common when the chart is revealed by a v-if/v-else flip
    // (e.g. after async data load) and the parent layout (splitter, flex)
    // has not resolved yet. One-way latch so a later collapse-to-zero
    // (splitter dragged shut) does not unmount the chart.
    const {width, height} = useElementSize(wrapperRef)
    const canRender = ref(false)
    let stopSizeWatch: (() => void) | null = null
    stopSizeWatch = watch([width, height], ([w, h]) => {
        if (w > 0 && h > 0) {
            canRender.value = true
            stopSizeWatch?.()
        }
    }, {immediate: true})

    interface EChartsTooltipParam {
        seriesName?: string
        name?: string
        value?: unknown
        color?: string
        /** Pre-built colored-dot HTML provided by ECharts. */
        marker?: string
        /** Present only for pie/donut chart items. */
        percent?: number
    }

    /**
     * Build tooltip HTML from the params ECharts passes to tooltip.formatter.
     * This reuses ECharts' own axis-snapping logic and the pre-computed marker
     * HTML, so no manual data indexing or color look-up is needed.
     */
    function buildContentFromParams(params: unknown): string {
        const list: EChartsTooltipParam[] = Array.isArray(params) ? params : [params as EChartsTooltipParam]
        if (!list.length) return ""

        const isPie = list[0]?.percent !== undefined

        const rows: string[] = []
        const category = list[0]?.name ?? ""

        if (category) {
            rows.push(`<div style="margin-bottom:4px;font-weight:600">${category}</div>`)
        }

        for (const p of list) {
            const marker = p.marker ?? `<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color ?? "currentColor"};margin-right:6px;vertical-align:middle;flex-shrink:0"></span>`
            const value = Array.isArray(p.value) ? p.value[1] ?? "—" : (p.value ?? "—")
            // For pie charts, seriesName is generic ("series0"); the meaningful label is
            // already shown in the header, so we only append the percentage.
            const label = isPie ? "" : (p.seriesName ?? "")
            const suffix = isPie ? ` (${p.percent}%)` : ""
            rows.push(
                `<div style="display:flex;align-items:center;line-height:20px">${marker}<span style="flex:1">${label}</span><span style="margin-left:12px;font-weight:600">${value}${suffix}</span></div>`,
            )
        }

        return rows.join("")
    }

    function onMouseleave() {
        tooltipVisible.value = false
    }

    defineExpose({
        getEchartsInstance: (): ECharts | null => (vChartRef.value?.chart as ECharts) ?? null,
        exportAsImage: (type: "jpeg" | "png" = "png", filename?: string): void => {
            if (!vChartRef.value) return
            const dataUrl = vChartRef.value.getDataURL({type, pixelRatio: 2, backgroundColor: "transparent"})
            if (!dataUrl) return
            const link = document.createElement("a")
            link.href = dataUrl
            link.download = filename || `chart.${type}`
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
        },
    })
</script>

<style scoped>
    .ks-chart-wrapper {
        position: relative;
        width: 100%;
        height: 100%;
    }

    .ks-chart__inner {
        width: 100%;
        height: 100%;
    }
</style>
