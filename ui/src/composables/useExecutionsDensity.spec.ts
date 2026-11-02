import {beforeEach, describe, expect, it, vi} from "vitest"
import {DENSITY_PAGE_SIZE, useExecutionsDensity} from "./useExecutionsDensity"

const dashboardStore = vi.hoisted(() => ({chartPreview: vi.fn()}))

vi.mock("../stores/dashboard", () => ({useDashboardStore: () => dashboardStore}))

const NOW_MS = Date.parse("2026-10-22T12:00:00.000Z")
const HOUR_MS = 3_600_000
const QUERY = {"filters[namespace][PREFIX]": "company"}

describe("useExecutionsDensity", () => {
    beforeEach(() => {
        dashboardStore.chartPreview.mockReset()
    })

    it("should bound every request and group the chart by namespace and flow", async () => {
        dashboardStore.chartPreview.mockResolvedValue({results: [], total: 0})

        await useExecutionsDensity().fetchDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        const [request] = dashboardStore.chartPreview.mock.calls[0]
        expect(request.globalFilter).toMatchObject({pageNumber: 1, pageSize: DENSITY_PAGE_SIZE})
        expect(request.chart).toMatch(/namespace:\s+field: NAMESPACE/)
        expect(request.chart).toMatch(/flowId:\s+field: FLOW_ID/)
    })

    it("should return the per-flow rows and their sum as the global density", async () => {
        dashboardStore.chartPreview.mockResolvedValue({
            results: [
                {date: "2026-10-22T11:00:00.000Z", state: "SUCCESS", namespace: "company", flowId: "a", total: 3},
                {date: "2026-10-22T11:00:00.000Z", state: "SUCCESS", namespace: "company", flowId: "b", total: 4},
            ],
            total: 2,
        })

        const density = await useExecutionsDensity().fetchDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(density.rowPoints.map(point => [point.flowId, point.count])).toEqual([["a", 3], ["b", 4]])
        expect(density.points).toEqual([{startMs: Date.parse("2026-10-22T11:00:00.000Z"), state: "SUCCESS", count: 7}])
        expect(density.isCapped).toBe(false)
    })

    it("should report a capped result when a response holds fewer rows than the chart produced", async () => {
        dashboardStore.chartPreview.mockResolvedValue({
            results: [{date: "2026-10-22T11:00:00.000Z", state: "SUCCESS", namespace: "company", flowId: "a", total: 3}],
            total: DENSITY_PAGE_SIZE + 5,
        })

        const density = await useExecutionsDensity().fetchDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(density.isCapped).toBe(true)
    })
})
