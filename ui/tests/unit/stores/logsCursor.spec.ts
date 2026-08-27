import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const searchLogs = vi.fn()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: any[]) => searchLogs(...args),
    deleteLogsFromFlow: vi.fn().mockResolvedValue({}),
}))

describe("logs store cursor pagination", {timeout: 15_000}, () => {
    beforeEach(() => {
        vi.resetModules()
        searchLogs.mockReset()
        setActivePinia(createPinia())
    })

    it("captures OFFSET mode with total from the response", async () => {
        searchLogs.mockResolvedValue({results: [{message: "a"}], total: 42, type: "OFFSET"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()

        await store.findLogs({page: 1, size: 25})

        expect(store.total).toBe(42)
        expect(store.isCursorMode).toBe(false)
        expect(store.nextCursor).toBeUndefined()
    })

    it("captures CURSOR mode: type + nextCursor, and total falls back to 0", async () => {
        searchLogs.mockResolvedValue({results: [{message: "a"}], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()

        await store.findLogs({page: 1, size: 25})

        expect(store.total).toBe(0)
        expect(store.isCursorMode).toBe(true)
        expect(store.hasNextCursor).toBe(true)
        expect(store.nextCursor).toBe("tok-1")
    })

    it("passes the cursor through to searchLogs when provided (Next)", async () => {
        searchLogs.mockResolvedValue({results: [], type: "CURSOR"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()

        await store.findLogs({page: 1, size: 25}, "tok-1")

        expect(searchLogs).toHaveBeenCalledWith(expect.objectContaining({cursor: "tok-1"}))
    })

    it("omits the cursor when not provided (first page / refresh)", async () => {
        searchLogs.mockResolvedValue({results: [], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()

        await store.findLogs({page: 1, size: 25})

        expect(searchLogs).toHaveBeenCalledWith(expect.objectContaining({cursor: undefined}))
    })

    it("clears nextCursor when a page returns without one (last/empty page)", async () => {
        searchLogs.mockResolvedValueOnce({results: [{message: "a"}], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        expect(store.hasNextCursor).toBe(true)

        searchLogs.mockResolvedValueOnce({results: [], type: "CURSOR"})
        await store.findLogs({page: 1, size: 25}, "tok-1")

        expect(store.hasNextCursor).toBe(false)
        expect(store.nextCursor).toBeUndefined()
    })

    it("re-derives mode on each response: CURSOR then OFFSET flips back to offset", async () => {
        searchLogs.mockResolvedValueOnce({results: [{message: "a"}], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        expect(store.isCursorMode).toBe(true)
        expect(store.hasNextCursor).toBe(true)

        searchLogs.mockResolvedValueOnce({results: [{message: "b"}], total: 7, type: "OFFSET"})
        await store.findLogs({page: 1, size: 25})

        expect(store.isCursorMode).toBe(false)
        expect(store.total).toBe(7)
        expect(store.hasNextCursor).toBe(false)
        expect(store.nextCursor).toBeUndefined()
    })

    it("loadNextPage advances using the current nextCursor and enables Previous", async () => {
        searchLogs.mockResolvedValueOnce({results: [{message: "p0"}], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        expect(store.hasPreviousPage).toBe(false)

        searchLogs.mockResolvedValueOnce({results: [{message: "p1"}], type: "CURSOR", nextCursor: "tok-2"})
        await store.loadNextPage({size: 25})

        expect(searchLogs).toHaveBeenLastCalledWith(expect.objectContaining({cursor: "tok-1"}))
        expect(store.logs).toEqual([{message: "p1"}])
        expect(store.nextCursor).toBe("tok-2")
        expect(store.hasPreviousPage).toBe(true)
    })

    it("loadNextPage on an empty page keeps current rows and drops Next (dead-end fix)", async () => {
        searchLogs.mockResolvedValueOnce({results: [{message: "last"}], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})

        searchLogs.mockResolvedValueOnce({results: [], type: "CURSOR"})
        await store.loadNextPage({size: 25})

        expect(store.logs).toEqual([{message: "last"}])
        expect(store.hasNextCursor).toBe(false)
        expect(store.hasPreviousPage).toBe(false)
    })

    it("loadPreviousPage returns to the prior page and disables Previous at the first page", async () => {
        searchLogs.mockResolvedValueOnce({results: [{message: "p0"}], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        searchLogs.mockResolvedValueOnce({results: [{message: "p1"}], type: "CURSOR", nextCursor: "tok-2"})
        await store.loadNextPage({size: 25})
        expect(store.hasPreviousPage).toBe(true)

        searchLogs.mockResolvedValueOnce({results: [{message: "p0"}], type: "CURSOR", nextCursor: "tok-1"})
        await store.loadPreviousPage({size: 25})

        expect(searchLogs).toHaveBeenLastCalledWith(expect.objectContaining({cursor: undefined}))
        expect(store.logs).toEqual([{message: "p0"}])
        expect(store.nextCursor).toBe("tok-1")
        expect(store.hasPreviousPage).toBe(false)
    })

    it("findLogs (fresh load) resets the back-stack", async () => {
        searchLogs.mockResolvedValueOnce({results: [{message: "p0"}], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("../../../src/stores/logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        searchLogs.mockResolvedValueOnce({results: [{message: "p1"}], type: "CURSOR", nextCursor: "tok-2"})
        await store.loadNextPage({size: 25})
        expect(store.hasPreviousPage).toBe(true)

        searchLogs.mockResolvedValueOnce({results: [{message: "fresh"}], type: "CURSOR", nextCursor: "tok-9"})
        await store.findLogs({page: 1, size: 25})

        expect(store.hasPreviousPage).toBe(false)
    })
})
