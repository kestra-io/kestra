import {beforeEach, describe, expect, it, vi} from "vitest"
import {MAX_PREVIEW_PAGE_SIZE, MAX_ROWS_PAGES, useExecutionsDensity} from "./useExecutionsDensity"

const dashboardStore = vi.hoisted(() => ({chartPreview: vi.fn()}))

vi.mock("../stores/dashboard", () => ({useDashboardStore: () => dashboardStore}))

const NOW_MS = Date.parse("2026-10-22T12:00:00.000Z")
const HOUR_MS = 3_600_000
const QUERY = {"filters[namespace][PREFIX]": "company"}

const row = (flowId: string, overrides: Record<string, unknown> = {}) => ({
    date: "2026-10-22T11:00:00.000Z", state: "SUCCESS", namespace: "company", flowId, total: 3, ...overrides,
})

describe("useExecutionsDensity", () => {
    beforeEach(() => {
        dashboardStore.chartPreview.mockReset()
    })

    it("should keep the toolbar density as one complete date and state query, without pagination", async () => {
        dashboardStore.chartPreview.mockResolvedValue({results: [row("a"), row("b", {total: 4})], total: 2})

        const density = await useExecutionsDensity().fetchDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        const [request] = dashboardStore.chartPreview.mock.calls[0]
        expect(request.globalFilter).not.toHaveProperty("pageSize")
        expect(request.chart).not.toMatch(/FLOW_ID/)
        expect(density.points).toHaveLength(2)
    })
})

describe("useExecutionsDensity fetchRowDensity", () => {
    beforeEach(() => {
        dashboardStore.chartPreview.mockReset()
    })

    it("should query the visible range per namespace and flow without asking for a page larger than the server accepts", async () => {
        dashboardStore.chartPreview.mockResolvedValue({results: [], total: 0})
        const startMs = NOW_MS - 2 * HOUR_MS

        await useExecutionsDensity().fetchRowDensity(QUERY, startMs, NOW_MS, NOW_MS)

        const [request] = dashboardStore.chartPreview.mock.calls[0]
        expect(request.globalFilter).toMatchObject({pageNumber: 1, pageSize: MAX_PREVIEW_PAGE_SIZE})
        expect(request.globalFilter.filters).toContainEqual({field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: new Date(startMs).toISOString()})
        expect(request.chart).toMatch(/namespace:\s+field: NAMESPACE/)
        expect(request.chart).toMatch(/flowId:\s+field: FLOW_ID/)
        expect(request.chart).toMatch(/orderBy:/)
        expect(MAX_PREVIEW_PAGE_SIZE).toBeLessThanOrEqual(1000)
    })

    it("should return the per-flow rows and drop the ones that start after the requested range", async () => {
        dashboardStore.chartPreview.mockResolvedValue({
            results: [row("a"), row("b", {total: 4}), row("late", {date: "2026-10-22T12:00:00.000Z"})],
            total: 3,
        })

        const rows = await useExecutionsDensity().fetchRowDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(rows.rowPoints.map(point => [point.flowId, point.count])).toEqual([["a", 3], ["b", 4]])
        expect(rows.isCapped).toBe(false)
    })

    it("should request every remaining page and merge them when a request holds more rows than one page", async () => {
        dashboardStore.chartPreview.mockImplementation(({globalFilter}) =>
            Promise.resolve({results: [row(`flow-page-${globalFilter.pageNumber}`)], total: 2500}),
        )

        const rows = await useExecutionsDensity().fetchRowDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        const requestedPages = dashboardStore.chartPreview.mock.calls.map(([request]) => request.globalFilter.pageNumber).sort()
        expect(requestedPages).toEqual([1, 2, 3])
        expect(dashboardStore.chartPreview.mock.calls.every(([request]) => request.globalFilter.pageSize <= 1000)).toBe(true)
        expect(rows.rowPoints.map(point => point.flowId).sort()).toEqual(["flow-page-1", "flow-page-2", "flow-page-3"])
        expect(rows.isCapped).toBe(false)
    })

    it("should count a row once when two pages return it", async () => {
        dashboardStore.chartPreview.mockResolvedValue({results: [row("a")], total: 2000})

        const rows = await useExecutionsDensity().fetchRowDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(rows.rowPoints).toHaveLength(1)
        expect(rows.rowPoints[0].count).toBe(3)
    })

    it("should stop after the first page and report a capped result when the rows exceed the page budget", async () => {
        dashboardStore.chartPreview.mockResolvedValue({results: [row("a")], total: (MAX_ROWS_PAGES + 1) * MAX_PREVIEW_PAGE_SIZE})

        const rows = await useExecutionsDensity().fetchRowDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(dashboardStore.chartPreview).toHaveBeenCalledTimes(1)
        expect(rows.isCapped).toBe(true)
    })

    it("should not report a capped result when the rows fill the page budget exactly", async () => {
        dashboardStore.chartPreview.mockResolvedValue({results: [row("a")], total: MAX_ROWS_PAGES * MAX_PREVIEW_PAGE_SIZE})

        const rows = await useExecutionsDensity().fetchRowDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(dashboardStore.chartPreview).toHaveBeenCalledTimes(MAX_ROWS_PAGES)
        expect(rows.isCapped).toBe(false)
    })

    it("should report the filters the chart could not apply", async () => {
        dashboardStore.chartPreview.mockResolvedValue({results: [], total: 0, ignoredFilters: ["q"]})

        const rows = await useExecutionsDensity().fetchRowDensity(QUERY, NOW_MS - 2 * HOUR_MS, NOW_MS, NOW_MS)

        expect(rows.ignoredFilters).toEqual(["q"])
    })
})
