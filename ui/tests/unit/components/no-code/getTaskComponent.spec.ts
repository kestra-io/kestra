import {describe, expect, it} from "vitest"
import {getTaskComponent} from "../../../../src/components/no-code/components/tasks/getTaskComponent"

// `definitions` is the global, class-name-keyed flow schema map — it has no top-level
// `.properties`, so it must never be the source of a task's own sibling property names.
const globalDefinitions = {
    "io.kestra.plugin.core.flow.Subflow": {properties: {namespace: {type: "string"}, flowId: {type: "string"}}},
    "io.kestra.plugin.kestra.dashboards.Export": {properties: {dashboardId: {type: "string"}, chartId: {type: "string"}}},
}

describe("getTaskComponent sibling-key dispatch", () => {
    it("dispatches flowId to subflow-id when siblingKeys includes namespace", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "flowId", ["namespace", "flowId"])
        expect(component.ksTaskName).toBe("subflow-id")
    })

    it("does not dispatch flowId to subflow-id without namespace in siblingKeys", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "flowId", ["flowId"])
        expect(component.ksTaskName).not.toBe("subflow-id")
    })

    it("falls back to plain string when siblingKeys is omitted", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "flowId")
        expect(component.ksTaskName).not.toBe("subflow-id")
    })

    it("dispatches chartId to chart-id when siblingKeys includes dashboardId", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "chartId", ["dashboardId", "chartId"])
        expect(component.ksTaskName).toBe("chart-id")
    })

    it("does not dispatch chartId to chart-id without dashboardId in siblingKeys", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "chartId", ["chartId"])
        expect(component.ksTaskName).not.toBe("chart-id")
    })

    it("dispatches dashboardId to dashboard-id regardless of siblingKeys", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "dashboardId")
        expect(component.ksTaskName).toBe("dashboard-id")
    })
})
