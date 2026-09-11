import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const searchLogs = vi.fn()
const downloadUrl = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs}))
vi.mock("../../../src/utils/utils", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../../../src/utils/utils")>()),
    downloadUrl,
}))

const {useLogsStore} = await import("../../../src/stores/logs")

/** One page of `size` synthetic rows; `formatLogsAsText` only needs these fields. */
const page = (count: number, total?: number, extra: Record<string, unknown> = {}) => ({
    results: Array.from({length: count}, (_, i) => ({
        timestamp: "2026-07-24T13:16:00.000Z",
        level: "INFO",
        message: `line ${i}`,
    })),
    total,
    ...extra,
})

describe("logs store downloadLogs", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        searchLogs.mockReset()
        downloadUrl.mockReset()
    })
    afterEach(() => localStorage.clear())

    it("should page through every page and report the export complete", async () => {
        searchLogs
            .mockResolvedValueOnce(page(1000, 1500))
            .mockResolvedValueOnce(page(500, 1500))

        const result = await useLogsStore().downloadLogs({})

        expect(searchLogs).toHaveBeenCalledTimes(2)
        expect(result).toEqual({downloaded: 1500, total: 1500, outcome: "complete"})
        expect(downloadUrl).toHaveBeenCalledOnce()
    })

    // The spec's original seven cases all had rows, which is how the silent no-op survived:
    // no file is written and the outcome is `complete`, so the caller had nothing to report.
    it("should report a complete export that matched nothing, with no file", async () => {
        searchLogs.mockResolvedValueOnce(page(0, 0))

        const result = await useLogsStore().downloadLogs({})

        expect(result).toEqual({downloaded: 0, total: 0, outcome: "complete"})
        expect(downloadUrl).not.toHaveBeenCalled()
    })

    it("should opt out of the global error toast on a failed page", async () => {
        searchLogs.mockRejectedValueOnce(new Error("boom"))

        await useLogsStore().downloadLogs({})

        expect(searchLogs.mock.calls[0][1]).toEqual({showMessageOnError: false})
    })

    it("should follow nextCursor under cursor pagination until an empty page", async () => {
        searchLogs
            .mockResolvedValueOnce(page(1000, undefined, {type: "CURSOR", nextCursor: "c1"}))
            .mockResolvedValueOnce(page(10, undefined, {type: "CURSOR", nextCursor: "c2"}))
            .mockResolvedValueOnce(page(0, undefined, {type: "CURSOR"}))

        const result = await useLogsStore().downloadLogs({})

        expect(searchLogs.mock.calls[1][0].cursor).toBe("c1")
        expect(searchLogs.mock.calls[2][0].cursor).toBe("c2")
        expect(result).toEqual({downloaded: 1010, total: undefined, outcome: "complete"})
    })

    // A cursor page may be short without being the last one — the backend signals the end with an
    // empty page, not with a partial one — so treating a short page as the end dropped the rest.
    it("should keep paging when a cursor page comes back short but still carries a cursor", async () => {
        searchLogs
            .mockResolvedValueOnce(page(10, undefined, {type: "CURSOR", nextCursor: "c1"}))
            .mockResolvedValueOnce(page(10, undefined, {type: "CURSOR", nextCursor: "c2"}))
            .mockResolvedValueOnce(page(0, undefined, {type: "CURSOR"}))

        const result = await useLogsStore().downloadLogs({})

        expect(searchLogs).toHaveBeenCalledTimes(3)
        expect(result.downloaded).toBe(20)
    })

    // Offset pagination has no empty-page sentinel: a short page is the last one there.
    it("should stop on a short page under offset pagination", async () => {
        searchLogs.mockResolvedValueOnce(page(10, 10))

        const result = await useLogsStore().downloadLogs({})

        expect(searchLogs).toHaveBeenCalledOnce()
        expect(result).toEqual({downloaded: 10, total: 10, outcome: "complete"})
    })

    // Regression: deriving truncation from the cap alone reported this as truncated with 0
    // skipped, because the cap check fires on a full final page.
    it("should report a complete export that lands exactly on the cap as complete", async () => {
        searchLogs.mockResolvedValue(page(1000, 50000))

        const result = await useLogsStore().downloadLogs({})

        expect(result).toEqual({downloaded: 50000, total: 50000, outcome: "complete"})
    })

    it("should report truncation with a skipped count when the total exceeds the cap", async () => {
        searchLogs.mockResolvedValue(page(1000, 60000))

        const result = await useLogsStore().downloadLogs({})

        expect(result.outcome).toBe("truncated")
        expect(result.downloaded).toBe(50000)
        expect(result.total).toBe(60000)
    })

    // No total under cursor pagination, so the cap is the only signal available.
    it("should report the cap when no total is available to quantify it", async () => {
        searchLogs.mockResolvedValue(page(1000, undefined, {type: "CURSOR", nextCursor: "c"}))

        const result = await useLogsStore().downloadLogs({})

        expect(result).toEqual({downloaded: 50000, total: undefined, outcome: "capped"})
    })

    // Elasticsearch refuses `from + size` beyond index.max_result_window rather than
    // returning a short page, so a mid-paging rejection must keep what was collected.
    it("should keep the pages already fetched when a later page is refused", async () => {
        searchLogs
            .mockResolvedValueOnce(page(1000, 12000))
            .mockRejectedValueOnce(new Error("search_phase_execution_exception"))

        const result = await useLogsStore().downloadLogs({})

        expect(result).toEqual({downloaded: 1000, total: 12000, outcome: "failed"})
        expect(downloadUrl).toHaveBeenCalledOnce()
    })

    it("should not write a file when the very first page is refused", async () => {
        searchLogs.mockRejectedValueOnce(new Error("boom"))

        const result = await useLogsStore().downloadLogs({})

        expect(result).toEqual({downloaded: 0, total: undefined, outcome: "failed"})
        expect(downloadUrl).not.toHaveBeenCalled()
    })
})
