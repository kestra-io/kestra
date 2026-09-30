import {defineStore} from "pinia"

import {computed, ref} from "vue"
import {apiUrlWithoutTenants} from "override/utils/route"

import {useClient} from "@kestra-io/kestra-sdk"
import type {Notification} from "@kestra-io/kestra-sdk"

export type {Notification} from "@kestra-io/kestra-sdk"

const HISTORY_PAGE_SIZE = 20

function isOngoing(notification: Notification): boolean {
    return notification.ongoing === true
}

function sortNewestFirst(notifications: Notification[]) {
    return [...notifications].sort((a, b) => {
        const diff = Date.parse(b.createdDate) - Date.parse(a.createdDate)
        if (diff !== 0) return diff
        return (a.id ?? "") < (b.id ?? "") ? 1 : -1
    })
}

export const useNotificationsStore = defineStore("notifications", () => {
    const axios = useClient()

    const notifications = ref<Notification[]>([])
    const unreadCount = ref(0)
    const nextCursor = ref<string | null>(null)

    const hasMoreHistory = computed(() => nextCursor.value !== null)

    const ongoingOperations = computed(() => notifications.value.filter(isOngoing))
    const staticNotifications = computed(() => notifications.value.filter(n => !isOngoing(n)))
    const hasOngoingOperation = computed(() => ongoingOperations.value.length > 0)
    const hasUnreadNotification = computed(() => notifications.value.some(n => !n.read))
    // DynamicScroller's scroll-end is level-triggered (re-fires on every render tick while the
    // last item stays visible, e.g. a short/still-loading list) — without this guard, a burst of
    // scroll-end events fires loadMore() concurrently, all reading the same not-yet-updated
    // nextCursor and requesting the same page over and over.
    const isLoadingHistory = ref(false)

    // Replaces an existing row in place (progress/read updates), or prepends a new one.
    function merge(incoming: Notification[]) {
        if (incoming.length === 0) return
        const byId = new Map(notifications.value.map(n => [n.id, n]))
        incoming.forEach(n => byId.set(n.id, n))
        notifications.value = sortNewestFirst(Array.from(byId.values()))
    }

    async function fetchUnreadCount() {
        const {data} = await axios.get(`${apiUrlWithoutTenants()}/notifications/unread-count`)
        unreadCount.value = data
    }

    // First page of history.
    async function loadInitial() {
        const {data} = await axios.get(`${apiUrlWithoutTenants()}/notifications/history`, {
            params: {limit: HISTORY_PAGE_SIZE},
        })
        notifications.value = sortNewestFirst(data.notifications)
        // The backend omits `nextCursor` entirely (not `null`) once history is exhausted, so
        // `data.nextCursor` is `undefined` there — coerce to `null` or hasMoreHistory's
        // `!== null` check never flips false and loadMore() keeps firing forever.
        nextCursor.value = data.nextCursor ?? null
        await fetchUnreadCount()
    }

    // Older page for infinite scroll — appended, never replaces what's already loaded.
    async function loadMore() {
        if (!hasMoreHistory.value || isLoadingHistory.value) return
        isLoadingHistory.value = true
        try {
            const {data} = await axios.get(`${apiUrlWithoutTenants()}/notifications/history`, {
                params: {limit: HISTORY_PAGE_SIZE, before: nextCursor.value},
            })
            merge(data.notifications)
            nextCursor.value = data.nextCursor ?? null
        } finally {
            isLoadingHistory.value = false
        }
    }

    // Optimistic: flip `read` locally first for instant feedback, then persist. If the POST
    // fails (e.g. the row was already deleted/purged server-side), revert the local flag and
    // refresh the badge from the server so the UI never shows a read state that didn't stick.
    async function markRead(id: string) {
        const notification = notifications.value.find(n => n.id === id)
        if (!notification || notification.read) return

        notification.read = true
        unreadCount.value = Math.max(0, unreadCount.value - 1)
        try {
            await axios.post(`${apiUrlWithoutTenants()}/notifications/${id}/read`)
        } catch (error) {
            notification.read = false
            await fetchUnreadCount()
            throw error
        }
    }

    // Mirror of markRead(): optimistic flip, revert + refresh the badge on failure.
    async function markUnread(id: string) {
        const notification = notifications.value.find(n => n.id === id)
        if (!notification || !notification.read) return

        notification.read = false
        unreadCount.value += 1
        try {
            await axios.post(`${apiUrlWithoutTenants()}/notifications/${id}/unread`)
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
            await axios.post(`${apiUrlWithoutTenants()}/notifications/read-all`)
        } catch (error) {
            previouslyUnread.forEach(n => {
                n.read = false
            })
            await fetchUnreadCount()
            throw error
        }
    }

    // Live push channel: a created/updated notification arrives here immediately. The SSE
    // frame's `id:` field carries "created"/"updated" (see NotificationEventType) — not
    // consumed here since merge() handles either the same way.
    let eventSource: EventSource | null = null

    function handleSseMessage(event: MessageEvent<string>) {
        const notification = JSON.parse(event.data) as Notification
        merge([notification])
        if (!isOngoing(notification)) {
            fetchUnreadCount()
        }
    }

    function startSSE() {
        stopSSE()
        eventSource = new EventSource(`${apiUrlWithoutTenants()}/notifications/follow`, {withCredentials: true})
        eventSource.onmessage = handleSseMessage
        fetchUnreadCount()
    }

    function stopSSE() {
        if (eventSource !== null) {
            eventSource.close()
            eventSource = null
        }
    }

    return {
        notifications,
        unreadCount,
        hasMoreHistory,
        ongoingOperations,
        staticNotifications,
        hasOngoingOperation,
        hasUnreadNotification,

        loadInitial,
        loadMore,
        markRead,
        markUnread,
        markAllRead,
        fetchUnreadCount,
        startSSE,
        stopSSE,
    }
})
