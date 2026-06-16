import type {Chart} from "../types.ts"
import {FilterObject} from "../../../utils/filters"

export const QUICK_FILTER_TABS = [
    {
        key: "all",
        token: "--ks-text-link",
        states: [] as string[],
    },
    {
        key: "running",
        token: "--ks-status-running",
        states: ["SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING", "KILLING"],
    },
    {
        key: "paused",
        token: "--ks-status-paused",
        states: ["PAUSED", "BREAKPOINT"],
    },
    {
        key: "success",
        token: "--ks-status-success",
        states: ["SUCCESS"],
    },
    {
        key: "warning",
        token: "--ks-status-warning",
        states: ["WARNING"],
    },
    {
        key: "failed",
        token: "--ks-status-error",
        states: ["FAILED", "KILLED", "CANCELLED", "SKIPPED", "RETRIED"],
    },
] as const

export type QuickFilterTabKey = (typeof QUICK_FILTER_TABS)[number]["key"]

export const ALL_STATES = QUICK_FILTER_TABS.flatMap((tab) => tab.states)

const EXECUTIONS_DATA_TYPE = "io.kestra.plugin.core.dashboard.data.Executions"

export const hasQuickFilters = (chart: Chart): boolean => {
    if (chart.data?.type !== EXECUTIONS_DATA_TYPE) return false
    const columns = chart.data?.columns ?? {}
    return Object.values(columns).some((col: Record<string, any>) => col.field === "STATE")
}

export const chartConstrainsState = (chart: Chart): boolean =>
    ((chart.data?.where as {field?: string}[] | undefined) ?? [])
        .some((condition) => String(condition?.field).toUpperCase() === "STATE")

export const stateFilterForTab = (chart: Chart, key: QuickFilterTabKey): FilterObject | null => {
    const tab = QUICK_FILTER_TABS.find((t) => t.key === key)
    if (!tab?.states.length) {
        return chartConstrainsState(chart)
            ? {field: "state", operation: "IN", value: [...ALL_STATES]}
            : null
    }
    return {field: "state", operation: "IN", value: [...tab.states]}
}
