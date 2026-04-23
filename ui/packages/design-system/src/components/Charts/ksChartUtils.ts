// ─── Shared utilities for Ks chart components ────────────────────────────────

export enum ChartFeature {
    LEGEND = "LEGEND",
    AXIS = "AXIS",
    AXIS_SPLITLINE = "AXIS_SPLITLINE",
    TOOLTIP = "TOOLTIP",
}

export enum TooltipType {
    NATIVE = "native",
    EXTERNAL = "external",
}

export enum ChartRenderer {
    CANVAS = "canvas",
    SVG = "svg",
}

export function deepMerge<T extends Record<string, unknown>>(
    base: T,
    override: Record<string, unknown>,
): T {
    const result: Record<string, unknown> = {...base}
    for (const key of Object.keys(override)) {
        const val = override[key]
        if (
            val !== undefined &&
            val !== null &&
            typeof val === "object" &&
            !Array.isArray(val) &&
            typeof result[key] === "object" &&
            result[key] !== null &&
            !Array.isArray(result[key])
        ) {
            result[key] = deepMerge(
                result[key] as Record<string, unknown>,
                val as Record<string, unknown>,
            )
        } else if (val !== undefined) {
            result[key] = val
        }
    }
    return result as T
}

/**
 * Apply an axis-level override to a single axis or every element of a multi-axis array.
 * When the base is an array (multiple axes) each entry is deep-merged individually so
 * the resulting array can safely replace it via deepMerge (arrays are never recursed).
 */
function applyToAxis(baseAxis: unknown, props: Record<string, unknown>): unknown {
    if (Array.isArray(baseAxis)) {
        return (baseAxis as Record<string, unknown>[]).map((axis) => deepMerge(axis, props))
    }

    // Single-object case: return props only — deepMerge will handle the merge with base.
    return props
}

/**
 * Build an ECharts option overlay that disables the requested features.
 * Pass `baseOption` so that multi-axis charts (yAxis as an array) are handled correctly.
 */
export function buildDisabledFeaturesOverride(
    features: ChartFeature[],
    baseOption?: Record<string, unknown>,
): Record<string, unknown> {
    const overlay: Record<string, unknown> = {}
    const xAxisProps: Record<string, unknown> = {}
    const yAxisProps: Record<string, unknown> = {}

    if (features.includes(ChartFeature.LEGEND)) {
        overlay.legend = {show: false}
    }

    if (features.includes(ChartFeature.AXIS)) {
        xAxisProps.show = false
        yAxisProps.show = false
        overlay.grid = {top: 2, right: 2, bottom: 2, left: 2, containLabel: false}
    }

    if (features.includes(ChartFeature.AXIS_SPLITLINE)) {
        xAxisProps.splitLine = {show: false}
        yAxisProps.splitLine = {show: false}
    }

    if (Object.keys(xAxisProps).length > 0) {
        overlay.xAxis = applyToAxis(baseOption?.xAxis, xAxisProps)
    }

    if (Object.keys(yAxisProps).length > 0) {
        overlay.yAxis = applyToAxis(baseOption?.yAxis, yAxisProps)
    }

    if (features.includes(ChartFeature.TOOLTIP)) {
        overlay.tooltip = {show: false}
    }

    return overlay
}
