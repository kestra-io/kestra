import {describe, expect, it} from "vitest"
import {
    stateFilterForTab,
    hasQuickFilters,
    chartConstrainsState,
    upcomingChartFor,
    ALL_STATES,
    QUICK_FILTER_TABS,
    UPCOMING_TAB,
} from "./quickFilters"
import type {Chart, Column} from "../types"

const EXECUTIONS = "io.kestra.plugin.core.dashboard.data.Executions"

const chart = (data: Record<string, unknown> = {}) => ({
    id: "c",
    type: "io.kestra.plugin.core.dashboard.chart.Table",
    data: {type: EXECUTIONS, columns: {state: {field: "STATE"}}, ...data},
}) as Chart

describe("quickFilters", () => {
    describe("stateFilterForTab", () => {
        it("returns null for the All tab when the chart does not constrain STATE", () => {
            expect(stateFilterForTab(chart(), "all")).toBeNull()
        })

        it("returns every state for the All tab when the chart constrains STATE", () => {
            const where = [{type: "IN", field: "STATE", values: ["RUNNING", "PAUSED"]}]
            expect(stateFilterForTab(chart({where}), "all")).toEqual({
                field: "state",
                operation: "IN",
                value: ALL_STATES,
            })
        })

        it("returns the tab's own states for a state tab", () => {
            expect(stateFilterForTab(chart(), "failed")).toEqual({
                field: "state",
                operation: "IN",
                value: ["FAILED", "KILLED", "CANCELLED", "SKIPPED", "RETRIED"],
            })
        })

        it("keeps the All tab as a superset of every state tab", () => {
            const where = [{type: "IN", field: "STATE", values: ["RUNNING"]}]
            const all = stateFilterForTab(chart({where}), "all")!.value as string[]
            for (const key of ["running", "paused", "success", "warning", "failed"] as const) {
                const states = stateFilterForTab(chart({where}), key)!.value as string[]
                expect(states.every((s) => all.includes(s))).toBe(true)
            }
        })
    })

    describe("chartConstrainsState", () => {
        it("is true when a STATE where exists", () => {
            expect(chartConstrainsState(chart({where: [{field: "STATE", values: []}]}))).toBe(true)
        })

        it("is case-insensitive on the field name", () => {
            expect(chartConstrainsState(chart({where: [{field: "state", values: []}]}))).toBe(true)
        })

        it("is false when only non-STATE wheres exist", () => {
            expect(chartConstrainsState(chart({where: [{field: "NAMESPACE", values: []}]}))).toBe(false)
        })

        it("is false when there is no where", () => {
            expect(chartConstrainsState(chart())).toBe(false)
        })
    })

    describe("hasQuickFilters", () => {
        it("is true for an executions table with a STATE column", () => {
            expect(hasQuickFilters(chart())).toBe(true)
        })

        it("is false for a non-executions data type", () => {
            expect(hasQuickFilters(chart({type: "io.kestra.plugin.core.dashboard.data.Logs"}))).toBe(false)
        })

        it("is false without a STATE column", () => {
            expect(hasQuickFilters(chart({columns: {id: {field: "ID"}}}))).toBe(false)
        })
    })

    describe("upcomingChartFor", () => {
        const LABELS = {namespace: "Namespace", flow: "Flow", trigger: "Trigger", nextExecution: "Next execution"}

        const upcoming = (source: Chart = chart({})) => upcomingChartFor(source, LABELS)

        it("builds a Triggers table derived from the source chart", () => {
            const result = upcoming()

            expect(result.id).toBe("c__upcoming")
            expect(result.type).toBe("io.kestra.plugin.core.dashboard.chart.Table")
            expect(result.data?.type).toBe("io.kestra.plugin.core.dashboard.data.Triggers")
        })

        it("labels its four columns from the labels it is given", () => {
            const columns = upcoming().data?.columns ?? {}

            expect(Object.values(columns).map((column: Column) => column.field))
                .toEqual(["NAMESPACE", "FLOW_ID", "TRIGGER_ID", "NEXT_EXECUTION_DATE"])
            expect(Object.values(columns).map((column: Column) => column.displayName))
                .toEqual(["Namespace", "Flow", "Trigger", "Next execution"])
        })

        it("orders the soonest run first and leaves disabled triggers out", () => {
            const {orderBy, where} = upcoming().data as Record<string, unknown>

            expect(orderBy).toEqual([{column: "next_execution_date", order: "ASC"}])
            expect(where).toEqual([{field: "DISABLED", type: "EQUAL_TO", value: false}])
        })

        it("mirrors the source chart's pagination", () => {
            expect(upcoming().chartOptions?.pagination?.enabled).toBe(false)

            const paginated = {...chart({}), chartOptions: {pagination: {enabled: true}}} as Chart
            expect(upcoming(paginated).chartOptions?.pagination?.enabled).toBe(true)
        })

        it("carries the whole definition as YAML, since it is rendered through the preview endpoint", () => {
            const content = upcoming().content ?? ""

            expect(content).toContain("io.kestra.plugin.core.dashboard.data.Triggers")
            expect(content).toContain("NEXT_EXECUTION_DATE")
            expect(content).toContain("c__upcoming")
        })
    })

    describe("UPCOMING_TAB", () => {
        it("stays out of the state tabs, so the All tab keeps its own states", () => {
            expect(QUICK_FILTER_TABS.map((tab) => tab.key)).not.toContain(UPCOMING_TAB.key)
            expect(ALL_STATES).toHaveLength(17)
        })
    })
})
