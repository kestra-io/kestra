import {describe, expect, it} from "vitest"
import {
    stateFilterForTab,
    hasQuickFilters,
    chartConstrainsState,
    countsByTab,
    ALL_STATES,
} from "./quickFilters"
import type {Chart} from "../types"

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

    describe("countsByTab", () => {
        it("sums the states of each tab and every state for All", () => {
            expect(countsByTab({SUCCESS: 9, FAILED: 2, KILLED: 1, RUNNING: 4, WARNING: 0})).toEqual({
                all: 16, running: 4, paused: 0, success: 9, warning: 0, failed: 3,
            })
        })

        it("counts a state that is in no tab only for All", () => {
            expect(countsByTab({SUCCESS: 1, SOMETHING_ELSE: 5}).all).toBe(6)
        })

        it("gives every tab zero when there are no executions", () => {
            expect(countsByTab({})).toEqual({all: 0, running: 0, paused: 0, success: 0, warning: 0, failed: 0})
        })
    })
})
