import type {Component} from "vue"
import {durationUtils} from "@kestra-io/design-system"
import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
import ClockAlertOutline from "vue-material-design-icons/ClockAlertOutline.vue"
import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
import HelpCircleOutline from "vue-material-design-icons/HelpCircleOutline.vue"

const STATUSES = ["fresh", "stale", "failed", "unknown"] as const

type AssetStatus = typeof STATUSES[number]

/** Every display site funnels through this, so a raw value never reaches `t()` or indexes the maps. */
export const normalizeStatus = (status?: string): AssetStatus =>
    STATUSES.find((known) => known === status) ?? "unknown"

const STATUS_ICONS: Record<AssetStatus, Component> = {
    fresh:   CheckCircle,
    stale:   ClockAlertOutline,
    failed:  AlertCircle,
    unknown: HelpCircleOutline,
}

export const statusIconOf = (status?: string): Component => STATUS_ICONS[normalizeStatus(status)]

const STATUS_TOKENS: Record<AssetStatus, string> = {
    fresh:   "--ks-status-success",
    stale:   "--ks-status-warning",
    failed:  "--ks-status-error",
    unknown: "--ks-status-neutral",
}

/** A `var()` for KsIcon's `color` prop: a scoped class lands on the icon root, where a `kel-icon` rule wins. */
export const statusColorOf = (status?: string): string => `var(${STATUS_TOKENS[normalizeStatus(status)]})`

/** Compact age: `1h`, `2d3h`. humanDuration takes seconds and is already localised. */
export const compactAge = (updated?: string): string | undefined => {
    if (!updated) {
        return undefined
    }

    const since = Date.now() - Date.parse(updated)
    if (Number.isNaN(since) || since < 0) {
        return undefined
    }

    return durationUtils.humanDuration(since / 1000)
}
