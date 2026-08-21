import {durationUtils} from "@kestra-io/design-system"

import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
import ClockAlertOutline from "vue-material-design-icons/ClockAlertOutline.vue"
import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
import CircleOutline from "vue-material-design-icons/CircleOutline.vue"
import HelpCircleOutline from "vue-material-design-icons/HelpCircleOutline.vue"

export const STATUSES = ["fresh", "stale", "failed", "never", "unknown"] as const

export type AssetStatus = typeof STATUSES[number]

/**
 * The five states this vocabulary knows, and "unknown" for anything else. Every display
 * site funnels through this: a raw value would otherwise reach `t()` and render the literal
 * key (`dependency.dag.status.FRESH`), and an inherited key like `toString` would resolve
 * a Function out of the maps below.
 */
export const normalizeStatus = (status?: string): AssetStatus =>
    (STATUSES as readonly string[]).includes(status ?? "") ? status as AssetStatus : "unknown"

/**
 * Freshness glyph per asset state, shared by the DAG card and the side list so the two
 * never drift into two glyph vocabularies for one set of states.
 *
 * `never` is hollow and `unknown` is a question mark: both render in
 * `--ks-status-neutral`, so without different shapes "has never run" and "we do not
 * track this" would look identical.
 */
export const STATUS_ICONS: Record<AssetStatus, unknown> = {
    fresh:   CheckCircle,
    stale:   ClockAlertOutline,
    failed:  AlertCircle,
    never:   CircleOutline,
    unknown: HelpCircleOutline,
}

export const statusIconOf = (status?: string): unknown => STATUS_ICONS[normalizeStatus(status)]

/**
 * Status token per state, for the states we actually know. `never` and `unknown` mean "no
 * signal" and stay neutral, which is what keeps a fresh asset and an untracked one from
 * reading the same.
 */
const STATUS_TOKENS: Partial<Record<AssetStatus, string>> = {
    fresh:  "--ks-status-success",
    stale:  "--ks-status-warning",
    failed: "--ks-status-error",
}

/**
 * Returned as a `var()` for KsIcon's `color` prop, which ElIcon applies as an inline style.
 * A scoped class cannot do this job: the class lands on the ElIcon root where a `kel-icon`
 * rule already sets `color`, so the glyph kept the default icon colour.
 */
export const statusColorOf = (status?: string): string =>
    `var(${STATUS_TOKENS[normalizeStatus(status)] ?? "--ks-status-neutral"})`

/**
 * Age since a timestamp, in the compact form the side list needs: `1h`, `2d3h`.
 *
 * `humanDuration` is the formatter AGENTS.md mandates, and it already ships single-letter
 * units per language (`fr` gives `a`/`j`), so this needs no new i18n key. It takes seconds,
 * and it pins `largest: 2` internally, so two units is the shortest it will go.
 */
export const compactAge = (updated?: string): string | undefined => {
    if (!updated) return undefined

    const since = Date.now() - Date.parse(updated)
    if (Number.isNaN(since) || since < 0) return undefined

    return durationUtils.humanDuration(since / 1000)
}
