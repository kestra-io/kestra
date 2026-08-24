import {durationUtils} from "@kestra-io/design-system"

import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
import ClockAlertOutline from "vue-material-design-icons/ClockAlertOutline.vue"
import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
import HelpCircleOutline from "vue-material-design-icons/HelpCircleOutline.vue"

export const STATUSES = ["fresh", "stale", "failed", "unknown"] as const

export type AssetStatus = typeof STATUSES[number]

/**
 * Every display site funnels through this: a raw value would otherwise reach `t()` and render
 * the literal key, and an inherited key like `toString` resolves a Function out of the maps.
 */
export const normalizeStatus = (status?: string): AssetStatus =>
    (STATUSES as readonly string[]).includes(status ?? "") ? status as AssetStatus : "unknown"

export const STATUS_ICONS: Record<AssetStatus, unknown> = {
    fresh:   CheckCircle,
    stale:   ClockAlertOutline,
    failed:  AlertCircle,
    unknown: HelpCircleOutline,
}

export const statusIconOf = (status?: string): unknown => STATUS_ICONS[normalizeStatus(status)]

/** `unknown` means "no signal", so it falls through to neutral below. */
const STATUS_TOKENS: Partial<Record<AssetStatus, string>> = {
    fresh:  "--ks-status-success",
    stale:  "--ks-status-warning",
    failed: "--ks-status-error",
}

/**
 * A `var()` for KsIcon's `color` prop, which it applies inline. A scoped class cannot do this:
 * it lands on the icon root where a `kel-icon` rule already sets `color`.
 */
export const statusColorOf = (status?: string): string =>
    `var(${STATUS_TOKENS[normalizeStatus(status)] ?? "--ks-status-neutral"})`

/** Compact age: `1h`, `2d3h`. humanDuration takes seconds and is already localised. */
export const compactAge = (updated?: string): string | undefined => {
    if (!updated) return undefined

    const since = Date.now() - Date.parse(updated)
    if (Number.isNaN(since) || since < 0) return undefined

    return durationUtils.humanDuration(since / 1000)
}
