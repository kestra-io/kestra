import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import type {Notification} from "./notifications"

const BASE_URL = "http://localhost/api/v1"

const axiosGet = vi.fn()
const axiosPost = vi.fn().mockResolvedValue({data: undefined})

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: axiosGet,
        post: axiosPost,
    }),
}))

vi.mock("override/utils/route", () => ({
    apiUrlWithoutTenants: () => BASE_URL,
}))

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

class MockEventSource {
    static instances: MockEventSource[] = []
    onmessage: ((event: MessageEvent<string>) => void) | null = null
    close = vi.fn()

    constructor(public url: string, public opts?: {withCredentials?: boolean}) {
        MockEventSource.instances.push(this)
    }
}

describe("notifications store", () => {
    beforeEach(() => {
        vi.resetModules()
        vi.useFakeTimers()
        axiosGet.mockReset()
        axiosGet.mockResolvedValue({data: 0})
        axiosPost.mockClear()
        MockEventSource.instances = []
        vi.stubGlobal("EventSource", MockEventSource)
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.unstubAllGlobals()
    })

    it("loadInitial() loads the first history page and the unread count", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification()], nextCursor: "cursor-1", serverTime: "2024-01-01T00:00:10Z"}})
            .mockResolvedValueOnce({data: 3})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()

        await store.loadInitial()

        expect(axiosGet).toHaveBeenNthCalledWith(1, `${BASE_URL}/notifications/history`, {params: {limit: 20}})
        expect(axiosGet).toHaveBeenNthCalledWith(2, `${BASE_URL}/notifications/unread-count`)
        expect(store.notifications).toHaveLength(1)
        expect(store.hasMoreHistory).toBe(true)
        expect(store.unreadCount).toBe(3)
    })

    it("loadMore() appends an older page using nextCursor and stops once exhausted", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification({id: "n2", createdDate: "2024-01-02T00:00:00Z"})], nextCursor: "cursor-1", serverTime: "t"}})
            .mockResolvedValueOnce({data: 0})
            .mockResolvedValueOnce({data: {notifications: [notification({id: "n1", createdDate: "2024-01-01T00:00:00Z"})], nextCursor: null, serverTime: "t"}})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        await store.loadMore()

        expect(axiosGet).toHaveBeenNthCalledWith(3, `${BASE_URL}/notifications/history`, {params: {limit: 20, before: "cursor-1"}})
        expect(store.notifications.map((n) => n.id)).toEqual(["n2", "n1"])
        expect(store.hasMoreHistory).toBe(false)

        await store.loadMore()
        expect(axiosGet).toHaveBeenCalledTimes(3)
    })

    it("loadMore() stops once exhausted even when the backend omits nextCursor entirely (not null)", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification({id: "n2", createdDate: "2024-01-02T00:00:00Z"})], nextCursor: "cursor-1", serverTime: "t"}})
            .mockResolvedValueOnce({data: 0})
            .mockResolvedValueOnce({data: {notifications: [notification({id: "n1", createdDate: "2024-01-01T00:00:00Z"})], serverTime: "t"}})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        await store.loadMore()

        expect(store.hasMoreHistory).toBe(false)

        await store.loadMore()
        expect(axiosGet).toHaveBeenCalledTimes(3)
    })

    it("loadInitial() treats an omitted nextCursor (fewer than a page of history) as exhausted", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification()], serverTime: "t"}})
            .mockResolvedValueOnce({data: 0})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()

        await store.loadInitial()

        expect(store.hasMoreHistory).toBe(false)

        await store.loadMore()
        expect(axiosGet).toHaveBeenCalledTimes(2)
    })

    it("sorts by actual instant, not lexicographic createdDate string (mixed offset formats)", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [
                notification({id: "early", createdDate: "2024-01-01T01:00:00+01:00"}),
                notification({id: "late", createdDate: "2024-01-01T00:00:05Z"}),
            ], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 2})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()

        await store.loadInitial()

        expect(store.notifications.map((n) => n.id)).toEqual(["late", "early"])
    })

    it("markRead() posts to /{id}/read and updates local state + unread count", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification()], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 1})
            .mockResolvedValueOnce({data: 0})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        await store.markRead("n1")

        expect(axiosPost).toHaveBeenCalledWith(`${BASE_URL}/notifications/n1/read`)
        expect(store.notifications[0].read).toBe(true)
        expect(store.unreadCount).toBe(0)
    })

    it("markRead() reverts the optimistic read flag and restores unreadCount when the POST rejects", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification()], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 1})
            .mockResolvedValueOnce({data: 1})
        axiosPost.mockRejectedValueOnce(new Error("network error"))
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        await expect(store.markRead("n1")).rejects.toThrow("network error")

        expect(store.notifications[0].read).toBe(false)
        expect(store.unreadCount).toBe(1)
    })

    it("markUnread() posts to /{id}/unread and updates local state + unread count", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification({read: true})], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 0})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        await store.markUnread("n1")

        expect(axiosPost).toHaveBeenCalledWith(`${BASE_URL}/notifications/n1/unread`)
        expect(store.notifications[0].read).toBe(false)
        expect(store.unreadCount).toBe(1)
    })

    it("markUnread() reverts the optimistic read flag and restores unreadCount when the POST rejects", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification({read: true})], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 0})
            .mockResolvedValueOnce({data: 0})
        axiosPost.mockRejectedValueOnce(new Error("network error"))
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        await expect(store.markUnread("n1")).rejects.toThrow("network error")

        expect(store.notifications[0].read).toBe(true)
        expect(store.unreadCount).toBe(0)
    })

    it("markAllRead() posts to /read-all and marks every loaded row read locally", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification(), notification({id: "n2"})], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 2})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        await store.markAllRead()

        expect(axiosPost).toHaveBeenCalledWith(`${BASE_URL}/notifications/read-all`)
        expect(store.notifications.every((n) => n.read)).toBe(true)
        expect(store.unreadCount).toBe(0)
    })

    it("markAllRead() reverts the optimistic read flags and restores unreadCount when the POST rejects", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification(), notification({id: "n2", read: true})], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 1})
            .mockResolvedValueOnce({data: 1})
        axiosPost.mockRejectedValueOnce(new Error("network error"))
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        await expect(store.markAllRead()).rejects.toThrow("network error")

        expect(store.notifications.find((n) => n.id === "n1")!.read).toBe(false)
        expect(store.notifications.find((n) => n.id === "n2")!.read).toBe(true)
        expect(store.unreadCount).toBe(1)
    })

    it("ongoingOperations/staticNotifications partition on the ongoing flag", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [
                notification({id: "running", ongoing: true}),
                notification({id: "done", ongoing: false}),
                notification({id: "one-shot"}),
            ], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 3})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        expect(store.ongoingOperations.map((n) => n.id)).toEqual(["running"])
        expect(store.staticNotifications.map((n) => n.id).sort()).toEqual(["done", "one-shot"])
    })

    it("hasOngoingOperation is true only while at least one notification is ongoing", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification({id: "done", ongoing: false})], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 0})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        expect(store.hasOngoingOperation).toBe(false)

        store.notifications.push(notification({id: "running", ongoing: true}))

        expect(store.hasOngoingOperation).toBe(true)
    })

    it("hasUnreadNotification is true only while at least one notification is unread", async () => {
        axiosGet
            .mockResolvedValueOnce({data: {notifications: [notification({id: "n1", read: true})], nextCursor: null, serverTime: "t"}})
            .mockResolvedValueOnce({data: 0})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()
        await store.loadInitial()

        expect(store.hasUnreadNotification).toBe(false)

        store.notifications.push(notification({id: "n2", read: false}))

        expect(store.hasUnreadNotification).toBe(true)
    })

    it("startSSE() opens a credentialed EventSource on /notifications/follow", async () => {
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()

        store.startSSE()

        expect(MockEventSource.instances).toHaveLength(1)
        expect(MockEventSource.instances[0].url).toBe(`${BASE_URL}/notifications/follow`)
        expect(MockEventSource.instances[0].opts).toEqual({withCredentials: true})
    })

    it("startSSE() merges a created/updated notification pushed over the stream and refreshes the unread count", async () => {
        axiosGet
            .mockResolvedValueOnce({data: 0})
            .mockResolvedValueOnce({data: 1})
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()

        store.startSSE()
        const pushed = notification({id: "pushed"})
        MockEventSource.instances[0].onmessage?.({data: JSON.stringify(pushed)} as MessageEvent<string>)
        await vi.waitFor(() => expect(axiosGet).toHaveBeenCalledWith(`${BASE_URL}/notifications/unread-count`))

        expect(store.notifications.map((n) => n.id)).toEqual(["pushed"])
        expect(store.unreadCount).toBe(1)
    })

    it("startSSE() closes a previous connection before opening a new one, and stopSSE() closes it", async () => {
        const {useNotificationsStore} = await import("./notifications")
        const store = useNotificationsStore()

        store.startSSE()
        const first = MockEventSource.instances[0]
        store.startSSE()

        expect(first.close).toHaveBeenCalled()
        expect(MockEventSource.instances).toHaveLength(2)

        store.stopSSE()
        expect(MockEventSource.instances[1].close).toHaveBeenCalled()
    })
})
