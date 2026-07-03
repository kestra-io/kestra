import {describe, test, expect, vi, beforeEach} from "vitest"

const mockFindExecutions = vi.fn()
const mockFindFlows = vi.fn()

vi.mock("../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({findExecutions: mockFindExecutions}),
}))
vi.mock("../../../src/stores/flow", () => ({
    useFlowStore: () => ({findFlows: mockFindFlows}),
}))

import {registerDrillDownPreview, getDrillDownPreview} from "../../../src/components/dashboard/composables/drillDownPreview"
import {DRILL_DOWNS} from "../../../src/components/dashboard/composables/chartDrillDown"

describe("drillDownPreview registry", () => {
    beforeEach(() => {
        mockFindExecutions.mockReset().mockResolvedValue({results: [{id: "e1"}], total: 1})
        mockFindFlows.mockReset().mockResolvedValue({results: [{id: "f1"}], total: 1})
    })

    test("executions/list is seeded as a table preview delegating to findExecutions with commit:false", async () => {
        const preview = getDrillDownPreview("executions/list")
        expect(preview?.mode).toBe("table")
        if (preview?.mode !== "table") throw new Error("expected table mode")

        const response = await preview.fetch({page: 1, size: 25})

        expect(mockFindExecutions).toHaveBeenCalledWith({page: 1, size: 25, commit: false})
        expect(response).toEqual({results: [{id: "e1"}], total: 1})
        expect(preview.rowDetail({namespace: "ns", flowId: "f", id: "e1"}, "main")).toEqual({
            name: "executions/update",
            params: {tenant: "main", namespace: "ns", flowId: "f", id: "e1"},
        })
    })

    test("flows/list is seeded as a table preview delegating to findFlows with commit:false", async () => {
        const preview = getDrillDownPreview("flows/list")
        expect(preview?.mode).toBe("table")
        if (preview?.mode !== "table") throw new Error("expected table mode")

        await preview.fetch({page: 1, size: 25})

        expect(mockFindFlows).toHaveBeenCalledWith({page: 1, size: 25, commit: false})
        expect(preview.rowDetail({namespace: "ns", id: "f1"}, "main")).toEqual({
            name: "flows/update",
            params: {tenant: "main", namespace: "ns", id: "f1"},
        })
    })

    test("logs/list is seeded as a logs preview (LogsWrapper owns its own fetch)", () => {
        expect(getDrillDownPreview("logs/list")).toEqual({mode: "logs"})
    })

    test("getDrillDownPreview returns undefined for an unregistered route", () => {
        expect(getDrillDownPreview("something/not-registered")).toBeUndefined()
    })

    test("registerDrillDownPreview adds a new entry and can override an existing one", () => {
        registerDrillDownPreview("custom/list", {mode: "none"})
        expect(getDrillDownPreview("custom/list")).toEqual({mode: "none"})

        registerDrillDownPreview("custom/list", {mode: "logs"})
        expect(getDrillDownPreview("custom/list")).toEqual({mode: "logs"})
    })

    // The enforcement guarantee: every entity registered via registerDrillDown (the encoder) must
    // also declare drawer behavior via registerDrillDownPreview (any mode, incl. "none"). Adding a
    // new OSS entity to the encoder without deciding its drawer behavior fails this test.
    test("coverage: every registered drill-down encoder route has a matching preview entry", () => {
        const routes = Object.values(DRILL_DOWNS).map(d => d.route)

        expect(routes.length).toBeGreaterThan(0)
        for (const route of routes) {
            expect(getDrillDownPreview(route)).toBeDefined()
        }
    })
})
