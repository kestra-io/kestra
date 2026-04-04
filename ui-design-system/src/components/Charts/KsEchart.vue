<template>
    <ks-tooltip
        v-if="tooltipType === TooltipType.EXTERNAL"
        trigger="manual"
        :visible="tooltipVisible"
        :content="tooltipContent"
        :raw-content="true"
        placement="bottom"
    >
        <div
            v-ks-loading="loading"
            class="ks-chart-wrapper"
            v-bind="$attrs"
            @mouseleave="onMouseleave"
        >
            <v-chart
                ref="vChartRef"
                class="ks-chart__inner"
                :theme="currentTheme"
                :option="effectiveOption"
                :init-options="{renderer: renderer}"
                autoresize
                @mouseover="emit('echarts-mouseover', $event)"
                @mouseout="emit('echarts-mouseout', $event)"
            />
        </div>
    </ks-tooltip>

    <div
        v-else
        v-ks-loading="loading"
        class="ks-chart-wrapper"
        v-bind="$attrs"
    >
        <v-chart
            ref="vChartRef"
            class="ks-chart__inner"
            :theme="currentTheme"
            :option="effectiveOption"
            :init-options="{renderer: renderer}"
            autoresize
            @mouseover="emit('echarts-mouseover', $event)"
            @mouseout="emit('echarts-mouseout', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted, nextTick} from "vue"
    import VChart from "vue-echarts"
    import type {ECharts} from "echarts/core"
    import {use} from "echarts/core"
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

    // ─── Types ────────────────────────────────────────────────────────────────

    export interface KsChartSeriesItem {
        name?: string
        [key: string]: unknown
    }

    // ─── Emits ────────────────────────────────────────────────────────────────

    const emit = defineEmits<{
        "echarts-mouseover": [params: unknown]
        "echarts-mouseout": [params: unknown]
    }>()

    // ─── Props ────────────────────────────────────────────────────────────────

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
            renderer: ChartRenderer.CANVAS
        },
    )

    // ─── Dark-mode detection ──────────────────────────────────────────────────

    const isDark = ref(false)

    function detectDark() {
        isDark.value = document.documentElement.classList.contains("dark")
    }

    let observer: MutationObserver | null = null
    let rafId: number | null = null

    onMounted(() => {
        detectDark()
        observer = new MutationObserver(detectDark)
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["class"],
        })
        // Defer resize until after the browser has done its first layout pass,
        // preventing the "Can't get DOM width or height" ECharts warning when
        // the container dimensions are not yet resolved at Vue mount time.
        rafId = requestAnimationFrame(() => {
            rafId = null
            ;(vChartRef.value?.chart as ECharts)?.resize()
        })
    })

    onUnmounted(() => {
        if (rafId !== null) {
            cancelAnimationFrame(rafId)
            rafId = null
        }
        observer?.disconnect()
    })

    // ─── Effective option (redirect native tooltip in external mode) ─────────

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

    // ─── Theme builder ────────────────────────────────────────────────────────

    const currentTheme = computed(() => {
        isDark.value // reactive dependency — triggers rebuild on theme change
        return KsTheme()
    })

    // ─── External tooltip ─────────────────────────────────────────────────────

    const vChartRef = ref<InstanceType<typeof VChart> | null>(null)
    const tooltipVisible = ref(false)
    const tooltipContent = ref("")

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

    // ─── Expose ───────────────────────────────────────────────────────────────

    defineExpose({
        getEchartsInstance: (): ECharts | null => (vChartRef.value?.chart as ECharts) ?? null,
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
