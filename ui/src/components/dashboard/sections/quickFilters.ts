import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"

import type {Chart, Column} from "../types.ts"
import {QueryFilter} from "@kestra-io/kestra-sdk"

export const QUICK_FILTER_TABS = [
    {
        key: "all",
        token: "--ks-text-link",
        states: [] as string[],
    },
    {
        key: "running",
        token: "--ks-status-running",
        states: ["SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "PAUSING", "RETRYING", "KILLING"],
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

/** Kept out of QUICK_FILTER_TABS: it carries no execution state, so it must not widen ALL_STATES. */
export const UPCOMING_TAB = {
    key: "upcoming",
    token: "--ks-text-link",
} as const

export type TabKey = QuickFilterTabKey | typeof UPCOMING_TAB.key

export const ALL_STATES = QUICK_FILTER_TABS.flatMap((tab) => tab.states)

const EXECUTIONS_DATA_TYPE = "io.kestra.plugin.core.dashboard.data.Executions"

const TRIGGERS_DATA_TYPE = "io.kestra.plugin.core.dashboard.data.Triggers"

const TABLE_CHART_TYPE = "io.kestra.plugin.core.dashboard.chart.Table"

export interface UpcomingColumnLabels {
    namespace: string;
    flow: string;
    trigger: string;
    nextExecution: string;
}

// The preview endpoint renders a chart from its YAML, so `content` has to carry the whole definition.
export const upcomingChartFor = (chart: Chart, labels: UpcomingColumnLabels): Chart => {
    const definition = {
        id: `${chart.id}__upcoming`,
        type: TABLE_CHART_TYPE,
        chartOptions: {
            displayName: chart.chartOptions?.displayName,
            pagination: {enabled: chart.chartOptions?.pagination?.enabled ?? false},
        },
        data: {
            type: TRIGGERS_DATA_TYPE,
            columns: {
                namespace: {field: "NAMESPACE", displayName: labels.namespace},
                flowId: {field: "FLOW_ID", displayName: labels.flow},
                triggerId: {field: "TRIGGER_ID", displayName: labels.trigger},
                next_execution_date: {field: "NEXT_EXECUTION_DATE", displayName: labels.nextExecution},
            },
            where: [{field: "DISABLED", type: "EQUAL_TO", value: false}],
            orderBy: [{column: "next_execution_date", order: "ASC"}],
        },
    }

    return {...definition, content: YAML_UTILS.stringify(definition)}
}

export const hasQuickFilters = (chart: Chart): boolean => {
    if (chart.data?.type !== EXECUTIONS_DATA_TYPE) return false
    const columns = chart.data?.columns ?? {}
    return Object.values(columns).some((col: Column) => col.field === "STATE")
}

export const chartConstrainsState = (chart: Chart): boolean =>
    ((chart.data?.where as {field?: string}[] | undefined) ?? [])
        .some((condition) => String(condition?.field).toUpperCase() === "STATE")

export const stateFilterForTab = (chart: Chart, key: QuickFilterTabKey): QueryFilter | null => {
    const tab = QUICK_FILTER_TABS.find((t) => t.key === key)
    if (!tab?.states.length) {
        return chartConstrainsState(chart)
            ? {field: "state", operation: "IN", value: [...ALL_STATES]}
            : null
    }
    return {field: "state", operation: "IN", value: [...tab.states]}
}
