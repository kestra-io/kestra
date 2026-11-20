import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

import type {LogEntry} from "@kestra-io/kestra-sdk"
import type {searchLogs as searchLogsSdk} from "@kestra-io/kestra-sdk/logs"

const searchLogs = vi.fn<typeof searchLogsSdk>()

vi.mock("@kestra-io/kestra-sdk/logs", () => ({
    searchLogs: (...args: Parameters<typeof searchLogsSdk>) => searchLogs(...args),
    deleteLogsFromFlow: vi.fn().mockResolvedValue({}),
}))

const log = (message: string): LogEntry => ({namespace: "test", flowId: "logs", message})

describe("logs store cursor pagination", () => {
    beforeEach(() => {
        vi.resetModules()
        searchLogs.mockReset()
        setActivePinia(createPinia())
    })

    it("captures OFFSET mode with total from the response", async () => {
        searchLogs.mockResolvedValue({results: [log("a")], total: 42, type: "OFFSET"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()

        await store.findLogs({page: 1, size: 25})

        expect(store.total).toBe(42)
        expect(store.isCursorMode).toBe(false)
        expect(store.nextCursor).toBeUndefined()
    })

    it("captures CURSOR mode: type + nextCursor, and total falls back to 0", async () => {
        searchLogs.mockResolvedValue({results: [log("a")], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()

        await store.findLogs({page: 1, size: 25})

        expect(store.total).toBe(0)
        expect(store.isCursorMode).toBe(true)
        expect(store.hasNextCursor).toBe(true)
        expect(store.nextCursor).toBe("tok-1")
    })

    it("passes the cursor through to searchLogs when provided (Next)", async () => {
        searchLogs.mockResolvedValue({results: [], type: "CURSOR"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()

        await store.findLogs({page: 1, size: 25}, "tok-1")

        expect(searchLogs).toHaveBeenCalledWith(expect.objectContaining({cursor: "tok-1"}))
    })

    it("omits the cursor when not provided (first page / refresh)", async () => {
        searchLogs.mockResolvedValue({results: [], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()

        await store.findLogs({page: 1, size: 25})

        expect(searchLogs).toHaveBeenCalledWith(expect.objectContaining({cursor: undefined}))
    })

    it("clears nextCursor when a page returns without one (last/empty page)", async () => {
        searchLogs.mockResolvedValueOnce({results: [log("a")], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        expect(store.hasNextCursor).toBe(true)

        searchLogs.mockResolvedValueOnce({results: [], type: "CURSOR"})
        await store.findLogs({page: 1, size: 25}, "tok-1")

        expect(store.hasNextCursor).toBe(false)
        expect(store.nextCursor).toBeUndefined()
    })

    it("re-derives mode on each response: CURSOR then OFFSET flips back to offset", async () => {
        searchLogs.mockResolvedValueOnce({results: [log("a")], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        expect(store.isCursorMode).toBe(true)
        expect(store.hasNextCursor).toBe(true)

        searchLogs.mockResolvedValueOnce({results: [log("b")], total: 7, type: "OFFSET"})
        await store.findLogs({page: 1, size: 25})

        expect(store.isCursorMode).toBe(false)
        expect(store.total).toBe(7)
        expect(store.hasNextCursor).toBe(false)
        expect(store.nextCursor).toBeUndefined()
    })

    it("loadNextPage advances using the current nextCursor and enables Previous", async () => {
        searchLogs.mockResolvedValueOnce({results: [log("p0")], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        expect(store.hasPreviousPage).toBe(false)

        searchLogs.mockResolvedValueOnce({results: [log("p1")], type: "CURSOR", nextCursor: "tok-2"})
        await store.loadNextPage({size: 25})

        expect(searchLogs).toHaveBeenLastCalledWith(expect.objectContaining({cursor: "tok-1"}))
        expect(store.logs).toEqual([log("p1")])
        expect(store.nextCursor).toBe("tok-2")
        expect(store.hasPreviousPage).toBe(true)
    })

    it("loadNextPage on an empty page keeps current rows and drops Next (dead-end fix)", async () => {
        searchLogs.mockResolvedValueOnce({results: [log("last")], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})

        searchLogs.mockResolvedValueOnce({results: [], type: "CURSOR"})
        await store.loadNextPage({size: 25})

        expect(store.logs).toEqual([log("last")])
        expect(store.hasNextCursor).toBe(false)
        expect(store.hasPreviousPage).toBe(false)
    })

    it("loadPreviousPage returns to the prior page and disables Previous at the first page", async () => {
        searchLogs.mockResolvedValueOnce({results: [log("p0")], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        searchLogs.mockResolvedValueOnce({results: [log("p1")], type: "CURSOR", nextCursor: "tok-2"})
        await store.loadNextPage({size: 25})
        expect(store.hasPreviousPage).toBe(true)

        searchLogs.mockResolvedValueOnce({results: [log("p0")], type: "CURSOR", nextCursor: "tok-1"})
        await store.loadPreviousPage({size: 25})

        expect(searchLogs).toHaveBeenLastCalledWith(expect.objectContaining({cursor: undefined}))
        expect(store.logs).toEqual([log("p0")])
        expect(store.nextCursor).toBe("tok-1")
        expect(store.hasPreviousPage).toBe(false)
    })

    it("findLogs (fresh load) resets the back-stack", async () => {
        searchLogs.mockResolvedValueOnce({results: [log("p0")], type: "CURSOR", nextCursor: "tok-1"})
        const {useLogsStore} = await import("./logs")
        const store = useLogsStore()
        await store.findLogs({page: 1, size: 25})
        searchLogs.mockResolvedValueOnce({results: [log("p1")], type: "CURSOR", nextCursor: "tok-2"})
        await store.loadNextPage({size: 25})
        expect(store.hasPreviousPage).toBe(true)

        searchLogs.mockResolvedValueOnce({results: [log("fresh")], type: "CURSOR", nextCursor: "tok-9"})
        await store.findLogs({page: 1, size: 25})

        expect(store.hasPreviousPage).toBe(false)
    })
})
