import {defineStore} from "pinia"
import {ref} from "vue"
import * as NotificationsAPI from "@kestra-io/kestra-sdk/notifications"
import {history_ as fetchHistory} from "@kestra-io/kestra-sdk/notifications"
import type {Notification} from "@kestra-io/kestra-sdk"

export type {Notification} from "@kestra-io/kestra-sdk"

const HISTORY_PAGE_SIZE = 20
const RECONNECT_DELAY_MS = 3000

/** Compares instants, not strings, since dates can carry different offset formats. */
function sortNewestFirst(notifications: Notification[]) {
    return [...notifications].sort((a, b) => {
        const diff = Date.parse(b.createdDate) - Date.parse(a.createdDate)
        if (diff !== 0) return diff
        return (a.id ?? "") < (b.id ?? "") ? 1 : -1
    })
}

export const useNotificationsStore = defineStore("notifications", () => {
    const notifications = ref<Notification[]>([])
    const unreadCount = ref(0)
    const nextCursor = ref<string | null>(null)
    /** The scroller's scroll-end fires on every render while the last row is visible, so pages must not overlap. */
    const isLoadingHistory = ref(false)

    /** Server clock of the last full read: what a dropped stream catches up from through `/since`. */
    let serverTime: string | null = null
    let followController: AbortController | null = null
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined

    /** Keeps the newer copy of a row, since a catch-up response can land after a pushed update and must not roll an operation back to ongoing. */
    function merge(incoming: Notification[]) {
        if (incoming.length === 0) return
        const byId = new Map(notifications.value.map(n => [n.id, n]))
        incoming.forEach(n => {
            const current = byId.get(n.id)
            if (!current || Date.parse(n.updatedDate) >= Date.parse(current.updatedDate)) {
                byId.set(n.id, n)
            }
        })
        notifications.value = sortNewestFirst(Array.from(byId.values()))
    }

    async function fetchUnreadCount() {
        unreadCount.value = await NotificationsAPI.unreadCount()
    }

    async function loadInitial() {
        const data = await fetchHistory({limit: HISTORY_PAGE_SIZE})
        notifications.value = sortNewestFirst(data.notifications ?? [])
        nextCursor.value = data.nextCursor ?? null
        serverTime = data.serverTime ?? null
        await fetchUnreadCount()
    }

    async function loadMore() {
        if (nextCursor.value === null || isLoadingHistory.value) return
        isLoadingHistory.value = true
        try {
            const data = await fetchHistory({limit: HISTORY_PAGE_SIZE, before: nextCursor.value})
            merge(data.notifications ?? [])
            nextCursor.value = data.nextCursor ?? null
        } finally {
            isLoadingHistory.value = false
        }
    }

    async function markRead(id: string) {
        const notification = notifications.value.find(n => n.id === id)
        if (!notification || notification.read) return

        notification.read = true
        unreadCount.value = Math.max(0, unreadCount.value - 1)
        try {
            await NotificationsAPI.markRead({id})
        } catch (error) {
            notification.read = false
            await fetchUnreadCount()
            throw error
        }
    }

    async function markUnread(id: string) {
        const notification = notifications.value.find(n => n.id === id)
        if (!notification || !notification.read) return

        notification.read = false
        unreadCount.value += 1
        try {
            await NotificationsAPI.markUnread({id})
        } catch (error) {
            notification.read = true
            await fetchUnreadCount()
            throw error
        }
    }

    async function markAllRead() {
        const previouslyUnread = notifications.value.filter(n => !n.read)
        previouslyUnread.forEach(n => {
            n.read = true
        })
        unreadCount.value = 0
        try {
            await NotificationsAPI.markAllRead()
        } catch (error) {
            previouslyUnread.forEach(n => {
                n.read = false
            })
            await fetchUnreadCount()
            throw error
        }
    }

    /** Without a baseline yet, the newest page stands in for one, which also surfaces operations started before this page loaded. */
    async function catchUp() {
        const data = serverTime === null
            ? await fetchHistory({limit: HISTORY_PAGE_SIZE})
            : await NotificationsAPI.pollSince({since: serverTime})
        merge(data.notifications ?? [])
        serverTime = data.serverTime ?? serverTime
        await fetchUnreadCount()
    }

    function receive(notification: Notification) {
        merge([notification])
        if (!notification.ongoing) {
            fetchUnreadCount().catch(() => undefined)
        }
    }

    /** Reconnects itself rather than through SDK retries, which leaked server memory (kestra-io/kestra#16982). */
    function startSSE() {
        stopSSE()
        const controller = new AbortController()
        followController = controller
        catchUp().catch(() => undefined)
        NotificationsAPI.listenUserNotifications({signal: controller.signal, sseMaxRetryAttempts: 1})
            .then(async ({stream}) => {
                for await (const event of stream) {
                    receive(event as unknown as Notification)
                }
            })
            .catch(() => undefined)
            .finally(() => {
                if (!controller.signal.aborted) {
                    reconnectTimer = setTimeout(startSSE, RECONNECT_DELAY_MS)
                }
            })
    }

    function stopSSE() {
        clearTimeout(reconnectTimer)
        followController?.abort()
        followController = null
    }

    return {
        notifications,
        unreadCount,

        loadInitial,
        loadMore,
        markRead,
        markUnread,
        markAllRead,
        startSSE,
        stopSSE,
    }
})
