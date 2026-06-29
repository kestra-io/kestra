import {describe, expect, it} from "vitest"
import {chartSegmentDrillDown} from "../../../src/components/dashboard/composables/chartDrillDown"
import {DEFAULT_BAR_CATEGORY_LIMIT, rankStackedBars} from "../../../src/components/dashboard/composables/charts"

const EXEC = "io.kestra.plugin.core.dashboard.data.Executions"
const LOGS = "io.kestra.plugin.core.dashboard.data.Logs"
const FLOWS = "io.kestra.plugin.core.dashboard.data.Flows"
const METRICS = "io.kestra.plugin.core.dashboard.data.Metrics"

describe("chartSegmentDrillDown", () => {
    it("reproduces the CLEID dashboard: clicked label + the chart's where conditions, on the executions route", () => {
        const chart = {
            data: {
                type: EXEC,
                where: [
                    {field: "STATE", type: "NOT_EQUAL_TO", value: "SUCCESS"},
                    {field: "LABELS", labelKey: "status", type: "NOT_EQUAL_TO", value: "init"},
                ],
            },
        }
        const result = chartSegmentDrillDown(chart, {field: "LABELS", labelKey: "cleid"}, "cleid-010")
        expect(result).toEqual({
            name: "executions/list",
            timeFiltered: true,
            query: {
                "filters[state][NOT_IN]": "SUCCESS",
                "filters[labels][NOT_EQUALS][status]": "init",
                "filters[labels][EQUALS][cleid]": "cleid-010",
            },
        })
    })

    it("maps a state-grouped executions pie to filters[state][IN] (state is multi-select)", () => {
        const result = chartSegmentDrillDown({data: {type: EXEC}}, {field: "STATE"}, "FAILED")
        expect(result).toEqual({name: "executions/list", timeFiltered: true, query: {"filters[state][IN]": "FAILED"}})
    })

    it("routes a Logs chart to the logs list with logs-specific filter keys", () => {
        const result = chartSegmentDrillDown({data: {type: LOGS}}, {field: "TASK_ID"}, "my-task")
        expect(result).toEqual({name: "logs/list", timeFiltered: true, query: {"filters[taskId][EQUALS]": "my-task"}})
    })

    it("returns null for a data source with no drill-down list (Metrics)", () => {
        expect(chartSegmentDrillDown({data: {type: METRICS}}, {field: "NAMESPACE"}, "x")).toBeNull()
    })

    it("returns null when the chart has no data type", () => {
        expect(chartSegmentDrillDown({data: {}}, {field: "STATE"}, "FAILED")).toBeNull()
        expect(chartSegmentDrillDown(undefined, undefined, "FAILED")).toBeNull()
    })

    it("skips where conditions and dimensions with no list equivalent (superset, never wrong rows)", () => {
        const chart = {
            data: {
                type: EXEC,
                where: [
                    {field: "DURATION", type: "GREATER_THAN", value: 60}, // no executions filter -> skipped
                    {field: "LABELS", type: "EQUAL_TO", value: "x"},      // no labelKey -> skipped
                    {field: "NAMESPACE", type: "EQUAL_TO", value: "io.kestra.test"},
                ],
            },
        }
        // NAMESPACE is multi-select -> EQUAL_TO becomes IN; clicked FLOW_ID dimension also multi-select -> IN
        const result = chartSegmentDrillDown(chart, {field: "FLOW_ID"}, "always-fail")
        expect(result).toEqual({
            name: "executions/list",
            timeFiltered: true,
            query: {
                "filters[namespace][IN]": "io.kestra.test",
                "filters[flowId][IN]": "always-fail",
            },
        })
    })

    it("joins array values for IN/NOT_IN where conditions", () => {
        const chart = {data: {type: EXEC, where: [{field: "STATE", type: "IN", value: ["FAILED", "WARNING"]}]}}
        const result = chartSegmentDrillDown(chart, {field: "LABELS", labelKey: "cleid"}, "cleid-001")
        expect(result?.query["filters[state][IN]"]).toBe("FAILED,WARNING")
    })

    it("routes a Flows chart to the flows list, carrying a namespace where", () => {
        const chart = {
            data: {
                type: FLOWS,
                where: [{field: "NAMESPACE", type: "NOT_EQUAL_TO", value: "system"}],
            },
        }
        const result = chartSegmentDrillDown(chart, {field: "NAMESPACE"}, "dashboard.test")
        expect(result).toEqual({
            name: "flows/list",
            timeFiltered: false, // flows have no time dimension
            query: {
                "filters[namespace][NOT_IN]": "system",
                "filters[namespace][IN]": "dashboard.test",
            },
        })
    })

    it("passes non-equality operators through on a multi-select field (namespace CONTAINS)", () => {
        const chart = {data: {type: EXEC, where: [{field: "NAMESPACE", type: "CONTAINS", value: "kestra"}]}}
        const result = chartSegmentDrillDown(chart, undefined, "x")
        expect(result?.query).toEqual({"filters[namespace][CONTAINS]": "kestra"})
    })
})

describe("rankStackedBars", () => {
    const opts = {categoryKey: "namespace", stackKeys: ["state"], valueKey: "count"}

    it("groups by category and produces one series per distinct stack key", () => {
        const rows = [
            {namespace: "a", state: "SUCCESS", count: 10},
            {namespace: "a", state: "FAILED", count: 2},
            {namespace: "b", state: "SUCCESS", count: 5},
        ]
        const result = rankStackedBars(rows, opts)
        expect(result.series.map((s) => s.name)).toEqual(expect.arrayContaining(["SUCCESS", "FAILED"]))
        expect(result.series.length).toBe(2)
    })

    it("ranks categories by total descending", () => {
        const rows = [
            {namespace: "low", state: "SUCCESS", count: 1},
            {namespace: "high", state: "SUCCESS", count: 100},
            {namespace: "mid", state: "SUCCESS", count: 50},
        ]
        const result = rankStackedBars(rows, opts)
        expect(result.categories).toEqual(["high", "mid", "low"])
        expect(result.totals).toEqual([100, 50, 1])
    })

    it("folds categories beyond the limit into Others, sets othersCount and othersNames", () => {
        const rows = Array.from({length: 10}, (_, i) => ({
            namespace: `ns-${i}`,
            state: "SUCCESS",
            count: 10 - i,
        }))
        const result = rankStackedBars(rows, {...opts, limit: 3})
        expect(result.categories.length).toBe(3)
        expect(result.othersCount).toBe(7)
        expect(result.othersNames.length).toBe(7)
        expect(result.othersNames).toEqual(expect.arrayContaining(["ns-3", "ns-4", "ns-5", "ns-6", "ns-7", "ns-8", "ns-9"]))
    })

    it("adds a trailing Others column on every series when folding", () => {
        const rows = Array.from({length: 5}, (_, i) => ({
            namespace: `ns-${i}`,
            state: "SUCCESS",
            count: 5 - i,
        }))
        const result = rankStackedBars(rows, {...opts, limit: 3})
        expect(result.series[0].data.length).toBe(4)
        const othersTotal = result.totals[result.totals.length - 1]
        expect(othersTotal).toBe(3) // ns-0=5,ns-1=4,ns-2=3 in top; ns-3=2,ns-4=1 folded -> 2+1=3
    })

    it("Others column totals reconcile with the sum of folded buckets", () => {
        const rows = [
            {namespace: "a", state: "SUCCESS", count: 10},
            {namespace: "a", state: "FAILED", count: 5},
            {namespace: "b", state: "SUCCESS", count: 8},
            {namespace: "c", state: "SUCCESS", count: 3},
            {namespace: "c", state: "FAILED", count: 2},
        ]
        const result = rankStackedBars(rows, {...opts, limit: 2})
        expect(result.othersCount).toBe(1)
        expect(result.othersNames).toEqual(["c"])
        const successSeries = result.series.find((s) => s.name === "SUCCESS")!
        const failedSeries = result.series.find((s) => s.name === "FAILED")!
        const othersIdx = result.categories.length
        expect(successSeries.data[othersIdx]).toBe(3)
        expect(failedSeries.data[othersIdx]).toBe(2)
        expect(result.totals[othersIdx]).toBe(5)
    })

    it("does not fold when categories are within the limit, othersCount is 0", () => {
        const rows = [
            {namespace: "a", state: "SUCCESS", count: 10},
            {namespace: "b", state: "SUCCESS", count: 5},
        ]
        const result = rankStackedBars(rows, opts)
        expect(result.othersCount).toBe(0)
        expect(result.othersNames).toEqual([])
        expect(result.series[0].data.length).toBe(2)
        expect(result.totals.length).toBe(2)
    })

    it("limit 0 disables folding and shows all categories", () => {
        const rows = Array.from({length: DEFAULT_BAR_CATEGORY_LIMIT + 5}, (_, i) => ({
            namespace: `ns-${i}`,
            state: "SUCCESS",
            count: 1,
        }))
        const result = rankStackedBars(rows, {...opts, limit: 0})
        expect(result.othersCount).toBe(0)
        expect(result.categories.length).toBe(DEFAULT_BAR_CATEGORY_LIMIT + 5)
    })

    it("negative limit disables folding", () => {
        const rows = Array.from({length: 5}, (_, i) => ({namespace: `ns-${i}`, state: "SUCCESS", count: 1}))
        const result = rankStackedBars(rows, {...opts, limit: -1})
        expect(result.othersCount).toBe(0)
        expect(result.categories.length).toBe(5)
    })

    it("returns empty categories and series for empty rows", () => {
        const result = rankStackedBars([], opts)
        expect(result.categories).toEqual([])
        expect(result.series).toEqual([])
        expect(result.totals).toEqual([])
        expect(result.othersCount).toBe(0)
        expect(result.othersNames).toEqual([])
    })

    it("sums multiple rows with the same category and stack key", () => {
        const rows = [
            {namespace: "a", state: "SUCCESS", count: 4},
            {namespace: "a", state: "SUCCESS", count: 6},
        ]
        const result = rankStackedBars(rows, opts)
        expect(result.categories).toEqual(["a"])
        expect(result.totals).toEqual([10])
        expect(result.series[0].data[0]).toBe(10)
    })

    it("aligns series data to categories order", () => {
        const rows = [
            {namespace: "low", state: "SUCCESS", count: 1},
            {namespace: "high", state: "SUCCESS", count: 100},
        ]
        const result = rankStackedBars(rows, opts)
        expect(result.categories[0]).toBe("high")
        const successSeries = result.series.find((s) => s.name === "SUCCESS")!
        expect(successSeries.data[0]).toBe(100)
        expect(successSeries.data[1]).toBe(1)
    })
})
