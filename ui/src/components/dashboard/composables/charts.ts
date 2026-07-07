import {ref} from "vue"

import {cssVar} from "@kestra-io/design-system"
import {getSchemeValue} from "../../../utils/scheme"

export interface RankedStackedBars {
    categories: string[]
    series: {name: string; data: number[]}[]
    totals: number[]
    othersCount: number
    othersNames: string[]
}

export const DEFAULT_BAR_CATEGORY_LIMIT = 8

export function rankStackedBars(
    rows: Record<string, unknown>[],
    opts: {categoryKey: string; stackKeys: string[]; valueKey: string; limit?: number},
): RankedStackedBars {
    const {categoryKey, stackKeys, valueKey} = opts
    const limit = opts.limit ?? DEFAULT_BAR_CATEGORY_LIMIT

    const grouped = new Map<string, Record<string, number>>()
    for (const row of rows ?? []) {
        const category = String(row[categoryKey] ?? "")
        const stack = stackKeys.map((k) => row[k]).join(", ")
        const value = Number(row[valueKey] ?? 0)
        let bucket = grouped.get(category)
        if (!bucket) {
            bucket = {}
            grouped.set(category, bucket)
        }
        bucket[stack] = (bucket[stack] ?? 0) + value
    }

    const totalOf = (bucket: Record<string, number>) => Object.values(bucket).reduce((a, b) => a + b, 0)

    const ranked = [...grouped.entries()]
        .map(([name, bucket]) => ({name, bucket, total: totalOf(bucket)}))
        .sort((a, b) => b.total - a.total)

    const stacks = [...new Set(ranked.flatMap((r) => Object.keys(r.bucket)))]

    const overflow = limit > 0 && ranked.length > limit
    const top = overflow ? ranked.slice(0, limit) : ranked
    const rest = overflow ? ranked.slice(limit) : []

    const categories = top.map((r) => r.name)
    const totals = top.map((r) => r.total)
    const series = stacks.map((stack) => ({name: stack, data: top.map((r) => r.bucket[stack] ?? 0)}))

    if (rest.length) {
        const othersBucket: Record<string, number> = {}
        for (const r of rest) for (const stack of stacks) othersBucket[stack] = (othersBucket[stack] ?? 0) + (r.bucket[stack] ?? 0)
        series.forEach((s) => s.data.push(othersBucket[s.name] ?? 0))
        totals.push(totalOf(othersBucket))
    }

    return {categories, series, totals, othersCount: rest.length, othersNames: rest.map((r) => r.name)}
}

function hashToHexColor(value: string): string {
    let hash = 0x811c9dc5
    for (let i = 0; i < value.length; i++) {
        hash ^= value.charCodeAt(i)
        hash = (hash * 0x01000193) >>> 0
    }

    hash ^= hash >>> 16
    hash *= 0x85ebca6b
    hash ^= hash >>> 13
    hash *= 0xc2b2ae35
    hash ^= hash >>> 16

    return `#${((hash >>> 0) & 0xffffff).toString(16).padStart(6, "0")}`
}

export function getConsistentHEXColor(_theme: "light" | "dark", value: string): string {
    const status = (value?.includes(",") ? value.split(",").pop()?.trim() : value) ?? ""

    const tokenColor = status ? cssVar(`--ks-chart-${status.toLowerCase()}`) : ""
    if (tokenColor) {
        return tokenColor
    }

    for (const scheme of ["executions", "logs"] as const) {
        const hex = getSchemeValue(status, scheme)
        if (hex && hex !== "transparent") {
            return hex
        }
    }

    return hashToHexColor(value ?? "")
}

export function getFormat(groupBy?: string): string | undefined {
    switch (groupBy) {
        case "minute":
            return "LT"
        case "hour":
            return "LLL"
        case "day":
        case "week":
            return "l"
        case "month":
            return "MM.YYYY"
    }
}

/**
 * Tracks which chart series are toggled off via the legend. Feed `legendSelected(names)`
 * into the ECharts `legend.selected` option so the hidden state survives chart re-renders
 * (an imperative `legendToggleSelect` dispatch resets on the next setOption).
 */
export function useLegendToggle() {
    const hidden = ref(new Set<string>())

    function onLegendToggle(name: string) {
        const next = new Set(hidden.value)
        if (next.has(name)) next.delete(name)
        else next.add(name)
        hidden.value = next
    }

    function legendSelected(names: (string | null | undefined)[]): Record<string, boolean> {
        return Object.fromEntries(
            names.filter((name): name is string => name != null).map((name) => [name, !hidden.value.has(name)]),
        )
    }

    return {onLegendToggle, legendSelected}
}
