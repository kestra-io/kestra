import {ref} from "vue"

import {cssVar, dateUtils, dayjs, type Dayjs} from "@kestra-io/design-system"
import {getSchemeValue} from "../../../utils/scheme"
import {DateGrouping} from "../../../utils/utils"
import type {ColorByValue} from "../types"

export interface RankedStackedBars {
    categories: string[]
    series: {name: string; data: number[]}[]
    totals: number[]
    othersCount: number
    othersNames: string[]
}

/**
 * The subset of an ECharts payload the dashboard charts read, shared by the `echarts-click` handlers
 * and the tooltip formatters. `KsBar`/`KsPie`/`KsEchart` emit `echarts-click` as `unknown` (the design
 * system doesn't depend on echarts' own event types), so this narrows it once for every reader.
 */
export interface EchartsParams {
    name?: string;
    seriesName?: string;
    seriesType?: string;
    dataIndex?: number;
    value?: unknown;
    /** Present only for pie/donut chart items. */
    percent?: number;
}

export function toEchartsParams(raw: unknown): EchartsParams {
    const field = (key: string): unknown => typeof raw === "object" && raw !== null ? Reflect.get(raw, key) : undefined
    const text = (key: string) => typeof field(key) === "string" ? String(field(key)) : undefined
    const num = (key: string) => typeof field(key) === "number" ? Number(field(key)) : undefined

    return {
        name: text("name"),
        seriesName: text("seriesName"),
        seriesType: text("seriesType"),
        dataIndex: num("dataIndex"),
        value: field("value"),
        percent: num("percent"),
    }
}

export const DEFAULT_BAR_CATEGORY_LIMIT = 8

/**
 * Dashboards hold many charts at once, so their canvases render at a capped pixel ratio rather than the full device
 * resolution. Canvas memory grows with the square of that ratio, and flat fills and thin lines lose next to nothing.
 */
export const DASHBOARD_CHART_MAX_PIXEL_RATIO = 1.5

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

export const MAX_FILLED_TIME_BUCKETS = 1000

/**
 * Returns every bucket label between the earliest and latest raw date at the given granularity, so that time
 * gaps between sparse buckets stay visible on a category axis instead of collapsing into adjacent bars.
 * Values that are not valid ISO dates are kept as labels verbatim; the fill stops at
 * {@link MAX_FILLED_TIME_BUCKETS} labels as a guard against absurd date ranges coming from bad data.
 */
export function fillTimeBucketLabels(rawDates: unknown[], grouping: DateGrouping): string[] {
    const labels = new Set<string>()
    const parsed: Dayjs[] = []

    for (const raw of rawDates ?? []) {
        if (raw === null || raw === undefined) continue
        const date = dateUtils.parseIso(raw)
        if (date.isValid()) {
            labels.add(date.format(grouping.format))
            parsed.push(date)
        } else {
            labels.add(String(raw))
        }
    }

    if (parsed.length) {
        const max = dayjs.max(parsed)!
        let cursor = dayjs.min(parsed)!.startOf(grouping.unit === "week" ? "isoWeek" : grouping.unit)
        for (let i = 0; cursor.isSameOrBefore(max) && i < MAX_FILLED_TIME_BUCKETS; i++) {
            labels.add(cursor.format(grouping.format))
            cursor = cursor.add(1, grouping.unit)
        }
    }

    return Array.from(labels).sort()
}

export const CATEGORICAL_SLOTS = 10

function hashString(value: string): number {
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

    return hash >>> 0
}

function hashToHexColor(value: string): string {
    return `#${(hashString(value) & 0xffffff).toString(16).padStart(6, "0")}`
}

function lastPart(value: string): string {
    return value.includes(",") ? value.split(",").pop()?.trim() ?? "" : value
}

function fixedColor(value: string, colorByValues?: ColorByValue[]): string | undefined {
    const rank = categoryRank(value, colorByValues)
    const configured = rank === undefined ? undefined : colorByValues?.[rank]?.color
    if (configured) {
        return configured.startsWith("#") ? configured : fixedColor(configured.toUpperCase())
    }

    const status = lastPart(value)
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

    return undefined
}

function paletteColor(slot: number, value: string): string {
    return cssVar(`--ks-chart-categorical-${slot + 1}`) || hashToHexColor(value)
}

export function getConsistentHEXColor(_theme: "light" | "dark", value: string | undefined): string {
    const text = value ?? ""
    return fixedColor(text) ?? paletteColor(hashString(text) % CATEGORICAL_SLOTS, text)
}

/** Colors of all the values of one chart: values with a configured, status or log level color keep it, the others get distinct palette slots, picked by hash so a value keeps its slot across charts unless two values collide, and once every slot is taken a color derived from the value. */
export function chartCategoryColors(_theme: "light" | "dark", values: (string | undefined)[], colorByValues?: ColorByValue[]): Map<string, string> {
    const result = new Map<string, string>()
    const unlisted: string[] = []
    for (const value of new Set(values.map((v) => v ?? ""))) {
        const color = fixedColor(value, colorByValues)
        if (color) {
            result.set(value, color)
        } else {
            unlisted.push(value)
        }
    }

    const taken = new Set<number>()
    for (const value of unlisted.sort()) {
        if (taken.size === CATEGORICAL_SLOTS) {
            result.set(value, hashToHexColor(value))
            continue
        }
        let slot = hashString(value) % CATEGORICAL_SLOTS
        while (taken.has(slot)) {
            slot = (slot + 1) % CATEGORICAL_SLOTS
        }
        taken.add(slot)
        result.set(value, paletteColor(slot, value))
    }

    return result
}

/** Position of a value in `colorByValues`, matching a stacked value by its last part too, or undefined when it is not listed. */
export function categoryRank(value: string, colorByValues?: ColorByValue[]): number | undefined {
    return [value, lastPart(value)]
        .map((key) => colorByValues?.findIndex((entry) => String(entry.value) === key) ?? -1)
        .find((index) => index >= 0)
}

export function byCategoryRank(colorByValues?: ColorByValue[]): (a: string, b: string) => number {
    const rank = (value: string) => categoryRank(value, colorByValues) ?? Number.MAX_SAFE_INTEGER
    return (a, b) => rank(a) - rank(b)
}

/** The configured label of a value, replacing each part of a stacked value on its own, or undefined when none is configured. */
export function categoryDisplayName(value: string, colorByValues?: ColorByValue[]): string | undefined {
    const labelOf = (key: string) => colorByValues?.find((entry) => String(entry.value) === key)?.label
    const parts = value.split(", ")
    return labelOf(value) ?? (parts.some((part) => labelOf(part) !== undefined)
        ? parts.map((part) => labelOf(part) ?? part).join(", ")
        : undefined)
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
