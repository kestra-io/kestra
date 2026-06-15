import {ref} from "vue"

import {cssVar} from "@kestra-io/design-system"
import {getSchemeValue} from "../../../utils/scheme"

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
