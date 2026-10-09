import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import type {Notification} from "./notifications"

const {fetchHistory, api} = vi.hoisted(() => ({
    fetchHistory: vi.fn(),
    api: {
        unreadCount: vi.fn(),
        pollSince: vi.fn(),
        markRead: vi.fn(),
        markUnread: vi.fn(),
        markAllRead: vi.fn(),
        listenUserNotifications: vi.fn(),
    },
}))

vi.mock("@kestra-io/kestra-sdk/notifications", () => ({...api, history_: fetchHistory}))

function notification(overrides: Partial<Notification> = {}): Notification {
    return {
        id: "n1",
        userId: "u1",
        tenantId: null,
        type: "GENERIC",
        title: "title",
        referenceId: null,
        succeededItems: null,
        failedItems: null,
        totalItems: null,
        read: false,
        createdDate: "2024-01-01T00:00:00Z",
        updatedDate: "2024-01-01T00:00:00Z",
        ...overrides,
    }
}

function streamOf(...events: Notification[]) {
    return Promise.resolve({stream: (async function* () {
        yield* events
    })()})
}

function streamOpenUntilAborted({signal}: {signal: AbortSignal}) {
    return Promise.resolve({stream: (async function* () {
        await new Promise((resolve) => signal.addEventListener("abort", resolve))
        yield* []
    })()})
}

async function useStore() {
    const {useNotificationsStore} = await import("./notifications")
    return useNotificationsStore()
}

describe("notifications store", () => {
    beforeEach(() => {
        vi.resetModules()
        vi.useFakeTimers()
        fetchHistory.mockReset()
        Object.values(api).forEach((fn) => fn.mockReset())
        api.unreadCount.mockResolvedValue(0)
        api.markRead.mockResolvedValue(undefined)
        api.markUnread.mockResolvedValue(undefined)
        api.markAllRead.mockResolvedValue({updated: 0})
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("loadInitial() loads the first history page and the unread count", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification()], nextCursor: "cursor-1", serverTime: "2024-01-01T00:00:10Z"})
        api.unreadCount.mockResolvedValueOnce(3)
        const store = await useStore()

        await store.loadInitial()

        expect(fetchHistory).toHaveBeenCalledWith({limit: 20})
        expect(store.notifications).toHaveLength(1)
        expect(store.unreadCount).toBe(3)
    })

    it("loadMore() appends an older page using nextCursor and stops once exhausted", async () => {
        fetchHistory
            .mockResolvedValueOnce({notifications: [notification({id: "n2", createdDate: "2024-01-02T00:00:00Z"})], nextCursor: "cursor-1"})
            .mockResolvedValueOnce({notifications: [notification({id: "n1", createdDate: "2024-01-01T00:00:00Z"})], nextCursor: null})
        const store = await useStore()
        await store.loadInitial()

        await store.loadMore()

        expect(fetchHistory).toHaveBeenLastCalledWith({limit: 20, before: "cursor-1"})
        expect(store.notifications.map((n) => n.id)).toEqual(["n2", "n1"])

        await store.loadMore()
        expect(fetchHistory).toHaveBeenCalledTimes(2)
    })

    it("loadMore() stops once exhausted even when the backend omits nextCursor entirely (not null)", async () => {
        fetchHistory
            .mockResolvedValueOnce({notifications: [notification({id: "n2", createdDate: "2024-01-02T00:00:00Z"})], nextCursor: "cursor-1"})
            .mockResolvedValueOnce({notifications: [notification({id: "n1", createdDate: "2024-01-01T00:00:00Z"})]})
        const store = await useStore()
        await store.loadInitial()

        await store.loadMore()
        await store.loadMore()

        expect(fetchHistory).toHaveBeenCalledTimes(2)
    })

    it("loadInitial() treats an omitted nextCursor (fewer than a page of history) as exhausted", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification()]})
        const store = await useStore()

        await store.loadInitial()
        await store.loadMore()

        expect(fetchHistory).toHaveBeenCalledTimes(1)
    })

    it("sorts by actual instant, not lexicographic createdDate string (mixed offset formats)", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [
            notification({id: "early", createdDate: "2024-01-01T01:00:00+01:00"}),
            notification({id: "late", createdDate: "2024-01-01T00:00:05Z"}),
        ], nextCursor: null})
        const store = await useStore()

        await store.loadInitial()

        expect(store.notifications.map((n) => n.id)).toEqual(["late", "early"])
    })

    it("markRead() persists the read flag and updates local state + unread count", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification()], nextCursor: null})
        api.unreadCount.mockResolvedValueOnce(1)
        const store = await useStore()
        await store.loadInitial()

        await store.markRead("n1")

        expect(api.markRead).toHaveBeenCalledWith({id: "n1"})
        expect(store.notifications[0].read).toBe(true)
        expect(store.unreadCount).toBe(0)
    })

    it("markRead() reverts the optimistic read flag and restores unreadCount when the request rejects", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification()], nextCursor: null})
        api.unreadCount.mockResolvedValue(1)
        api.markRead.mockRejectedValueOnce(new Error("network error"))
        const store = await useStore()
        await store.loadInitial()

        await expect(store.markRead("n1")).rejects.toThrow("network error")

        expect(store.notifications[0].read).toBe(false)
        expect(store.unreadCount).toBe(1)
    })

    it("markUnread() persists the unread flag and updates local state + unread count", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification({read: true})], nextCursor: null})
        const store = await useStore()
        await store.loadInitial()

        await store.markUnread("n1")

        expect(api.markUnread).toHaveBeenCalledWith({id: "n1"})
        expect(store.notifications[0].read).toBe(false)
        expect(store.unreadCount).toBe(1)
    })

    it("markUnread() reverts the optimistic read flag and restores unreadCount when the request rejects", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification({read: true})], nextCursor: null})
        api.markUnread.mockRejectedValueOnce(new Error("network error"))
        const store = await useStore()
        await store.loadInitial()

        await expect(store.markUnread("n1")).rejects.toThrow("network error")

        expect(store.notifications[0].read).toBe(true)
        expect(store.unreadCount).toBe(0)
    })

    it("markAllRead() persists and marks every loaded row read locally", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification(), notification({id: "n2"})], nextCursor: null})
        api.unreadCount.mockResolvedValueOnce(2)
        const store = await useStore()
        await store.loadInitial()

        await store.markAllRead()

        expect(api.markAllRead).toHaveBeenCalled()
        expect(store.notifications.every((n) => n.read)).toBe(true)
        expect(store.unreadCount).toBe(0)
    })

    it("markAllRead() reverts the optimistic read flags and restores unreadCount when the request rejects", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification(), notification({id: "n2", read: true})], nextCursor: null})
        api.unreadCount.mockResolvedValue(1)
        api.markAllRead.mockRejectedValueOnce(new Error("network error"))
        const store = await useStore()
        await store.loadInitial()

        await expect(store.markAllRead()).rejects.toThrow("network error")

        expect(store.notifications.find((n) => n.id === "n1")?.read).toBe(false)
        expect(store.notifications.find((n) => n.id === "n2")?.read).toBe(true)
        expect(store.unreadCount).toBe(1)
    })

    it("startSSE() merges a notification pushed over the stream and refreshes the unread count", async () => {
        fetchHistory.mockResolvedValue({notifications: []})
        api.unreadCount.mockResolvedValueOnce(0).mockResolvedValueOnce(1)
        api.listenUserNotifications.mockImplementationOnce(() => streamOf(notification({id: "pushed"}))).mockImplementation(streamOpenUntilAborted)
        const store = await useStore()

        store.startSSE()

        await vi.waitFor(() => expect(store.unreadCount).toBe(1))
        expect(store.notifications.map((n) => n.id)).toEqual(["pushed"])
        store.stopSSE()
    })

    it("startSSE() surfaces an operation started before the page loaded, so the bell knows about it before any update is pushed", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification({id: "running", ongoing: true})], serverTime: "2024-01-01T00:00:10Z"})
        api.listenUserNotifications.mockImplementation(streamOpenUntilAborted)
        const store = await useStore()

        store.startSSE()

        await vi.waitFor(() => expect(store.notifications.map((n) => n.ongoing)).toEqual([true]))
        expect(fetchHistory).toHaveBeenCalledWith({limit: 20})
        store.stopSSE()
    })

    it("startSSE() catches up from the last server time once a dropped stream reconnects, including updates to rows outside the first page", async () => {
        fetchHistory.mockResolvedValueOnce({notifications: [notification({id: "recent"})], serverTime: "2024-01-01T00:00:10Z"})
        api.pollSince
            .mockResolvedValueOnce({notifications: [notification({id: "older-operation", createdDate: "2023-12-01T00:00:00Z", ongoing: false, outcome: "SUCCEEDED"})], serverTime: "2024-01-01T00:05:00Z"})
            .mockResolvedValue({notifications: [], serverTime: "2024-01-01T00:10:00Z"})
        api.listenUserNotifications
            .mockImplementationOnce(() => streamOf())
            .mockImplementationOnce(() => streamOf())
            .mockImplementation(streamOpenUntilAborted)
        const store = await useStore()

        store.startSSE()
        await vi.advanceTimersByTimeAsync(3000)

        await vi.waitFor(() => expect(store.notifications.map((n) => n.id)).toEqual(["recent", "older-operation"]))
        expect(api.pollSince).toHaveBeenCalledWith({since: "2024-01-01T00:00:10Z"})

        await vi.advanceTimersByTimeAsync(3000)

        await vi.waitFor(() => expect(api.pollSince).toHaveBeenLastCalledWith({since: "2024-01-01T00:05:00Z"}))
        store.stopSSE()
    })

    it("startSSE() keeps a pushed update when a catch-up response carrying an older version of the row lands after it", async () => {
        const finished = notification({id: "op", ongoing: false, outcome: "SUCCEEDED", updatedDate: "2024-01-01T00:01:00Z"})
        const stale = notification({id: "op", ongoing: true, updatedDate: "2024-01-01T00:00:30Z"})
        let releaseCatchUp: () => void = () => undefined
        fetchHistory.mockReturnValueOnce(new Promise((resolve) => {
            releaseCatchUp = () => resolve({notifications: [stale], serverTime: "2024-01-01T00:00:40Z"})
        }))
        api.listenUserNotifications.mockImplementationOnce(() => streamOf(finished)).mockImplementation(streamOpenUntilAborted)
        const store = await useStore()

        store.startSSE()
        await vi.waitFor(() => expect(store.notifications).toHaveLength(1))
        releaseCatchUp()
        await vi.waitFor(() => expect(api.unreadCount).toHaveBeenCalledTimes(2))

        expect(store.notifications[0].ongoing).toBe(false)
        store.stopSSE()
    })

    it("stopSSE() aborts the stream and does not reconnect", async () => {
        fetchHistory.mockResolvedValue({notifications: []})
        api.listenUserNotifications.mockImplementation(streamOpenUntilAborted)
        const store = await useStore()

        store.startSSE()
        store.stopSSE()
        await vi.advanceTimersByTimeAsync(10000)

        expect(api.listenUserNotifications.mock.calls[0][0].signal.aborted).toBe(true)
        expect(api.listenUserNotifications).toHaveBeenCalledTimes(1)
    })
})
