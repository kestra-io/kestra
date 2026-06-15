<template>
    <KsTooltip
        v-if="tooltipType === TooltipType.EXTERNAL"
        trigger="manual"
        :visible="tooltipVisible"
        :content="tooltipContent"
        :rawContent="true"
        placement="bottom"
        popperClass="ks-chart-tooltip"
        :popperOptions="tooltipPopperOptions"
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
                @click="emit('echarts-click', $event)"
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
            @click="emit('echarts-click', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted, watch} from "vue"

    import {useElementSize} from "@vueuse/core"
    import VChart from "vue-echarts"
    import {use, type ECharts} from "echarts/core"
    import {CanvasRenderer, SVGRenderer} from "echarts/renderers"
    import {GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, GraphicComponent} from "echarts/components"

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
        "echarts-click": [params: unknown]
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
        observer.observe(document.documentElement, {attributes: true, attributeFilter: ["class"]})
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
                    confine: false,
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
        void isDark.value
        return KsTheme()
    })

    const vChartRef = ref<InstanceType<typeof VChart> | null>(null)
    const wrapperRef = ref<HTMLElement | null>(null)
    const tooltipVisible = ref(false)
    const tooltipContent = ref("")

    const tooltipPopperOptions = {
        modifiers: [
            {name: "flip", options: {rootBoundary: "viewport", padding: 8}},
            {name: "preventOverflow", options: {rootBoundary: "viewport", padding: 8}},
        ],
    }

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
        seriesType?: string
        name?: string
        value?: unknown
        color?: string
        marker?: string
        /** Present only for pie/donut chart items. */
        percent?: number
    }

    function toCapitalCase(text: string): string {
        return text.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    }

    function buildContentFromParams(params: unknown): string {
        const list: EChartsTooltipParam[] = Array.isArray(params) ? params : [params as EChartsTooltipParam]
        if (!list.length) return ""

        const isPie = list[0]?.percent !== undefined

        const rows: string[] = []
        const category = list[0]?.name ?? ""

        if (category) {
            rows.push(`<div style="margin-bottom:6px;font-weight:600;color:var(--ks-text-primary)">${isPie ? toCapitalCase(category) : category}</div>`)
        }

        for (const p of list) {
            const value = Array.isArray(p.value) ? p.value[1] : p.value
            if (value === 0 || value === undefined || value === null) {
                continue
            }
            const swatch = p.seriesType === "line"
                ? `<span style="display:inline-block;width:14px;height:2px;border-radius:2px;background:${p.color ?? "currentColor"};flex-shrink:0"></span>`
                : `<span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:${p.color ?? "currentColor"};flex-shrink:0"></span>`
            const label = isPie ? "" : toCapitalCase(p.seriesName ?? "")
            const suffix = isPie ? ` (${p.percent}%)` : ""
            rows.push(
                `<div style="display:flex;align-items:center;gap:6px;line-height:18px;white-space:nowrap">${swatch}<span style="flex:1">${label}</span><span style="margin-left:12px">${value}${suffix}</span></div>`,
            )
        }

        return `<div style="font-size:var(--ks-font-size-2xs);color:var(--ks-text-secondary);font-variant-numeric:tabular-nums">${rows.join("")}</div>`
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

    :global(.ks-chart-tooltip) {
        max-width: min(20rem, 90vw);
        overflow-wrap: anywhere;
    }
</style>
