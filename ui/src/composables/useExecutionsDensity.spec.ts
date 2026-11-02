import {beforeEach, describe, expect, it, vi} from "vitest"
import {MAX_DENSITY_PAGES, MAX_PREVIEW_PAGE_SIZE, useExecutionsDensity} from "./useExecutionsDensity"

const dashboardStore = vi.hoisted(() => ({chartPreview: vi.fn()}))

vi.mock("../stores/dashboard", () => ({useDashboardStore: () => dashboardStore}))

const NOW_MS = Date.parse("2026-10-22T12:00:00.000Z")
const HOUR_MS = 3_600_000
const QUERY = {"filters[namespace][PREFIX]": "company"}

describe("useExecutionsDensity", () => {
    beforeEach(() => {
        dashboardStore.chartPreview.mockReset()
    })

    it("should group the chart by namespace and flow and never ask for a page larger than the server accepts", async () => {
        dashboardStore.chartPreview.mockResolvedValue({results: [], total: 0})

        await useExecutionsDensity().fetchDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        const [request] = dashboardStore.chartPreview.mock.calls[0]
        expect(request.globalFilter).toMatchObject({pageNumber: 1, pageSize: MAX_PREVIEW_PAGE_SIZE})
        expect(MAX_PREVIEW_PAGE_SIZE).toBeLessThanOrEqual(1000)
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

    it("should request every remaining page and merge them when a request holds more rows than one page", async () => {
        const row = (flowId: string) => ({date: "2026-10-22T11:00:00.000Z", state: "SUCCESS", namespace: "company", flowId, total: 1})
        dashboardStore.chartPreview.mockImplementation(({globalFilter}) =>
            Promise.resolve({results: [row(`flow-page-${globalFilter.pageNumber}`)], total: 2500}),
        )

        const density = await useExecutionsDensity().fetchDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        const requestedPages = dashboardStore.chartPreview.mock.calls.map(([request]) => request.globalFilter.pageNumber).sort()
        expect(requestedPages).toEqual([1, 2, 3])
        expect(dashboardStore.chartPreview.mock.calls.every(([request]) => request.globalFilter.pageSize <= 1000)).toBe(true)
        expect(density.rowPoints.map(point => point.flowId).sort()).toEqual(["flow-page-1", "flow-page-2", "flow-page-3"])
        expect(density.points[0].count).toBe(3)
        expect(density.isCapped).toBe(false)
    })

    it("should report a capped result and stop paging when a request exceeds the page budget", async () => {
        dashboardStore.chartPreview.mockResolvedValue({
            results: [{date: "2026-10-22T11:00:00.000Z", state: "SUCCESS", namespace: "company", flowId: "a", total: 3}],
            total: (MAX_DENSITY_PAGES + 1) * MAX_PREVIEW_PAGE_SIZE,
        })

        const density = await useExecutionsDensity().fetchDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(dashboardStore.chartPreview).toHaveBeenCalledTimes(MAX_DENSITY_PAGES)
        expect(density.isCapped).toBe(true)
    })

    it("should not report a capped result when the rows fill the page budget exactly", async () => {
        dashboardStore.chartPreview.mockResolvedValue({
            results: [{date: "2026-10-22T11:00:00.000Z", state: "SUCCESS", namespace: "company", flowId: "a", total: 3}],
            total: MAX_DENSITY_PAGES * MAX_PREVIEW_PAGE_SIZE,
        })

        const density = await useExecutionsDensity().fetchDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(density.isCapped).toBe(false)
    })
})
