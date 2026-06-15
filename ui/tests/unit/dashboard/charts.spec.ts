import {describe, expect, it} from "vitest"
import {chartSegmentDrillDown} from "../../../src/components/dashboard/composables/charts"

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
