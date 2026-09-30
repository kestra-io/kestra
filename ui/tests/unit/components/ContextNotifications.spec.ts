import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {i18nMount} from "../i18nMount"

import {createPinia, setActivePinia} from "pinia"
import {createRouter, createWebHistory} from "vue-router"
import type {Notification} from "../../../src/stores/notifications"

// A local router (rather than a globally-installed one): most OSS unit tests mount in
// isolation without vue-router, so this component's own useRouter()/useRoute() injections
// need one supplied here. Spy on `push` for assertions.
const router = createRouter({
    history: createWebHistory(),
    routes: [{path: "/mock/:tenant?/:caseId?", name: "cases/view", component: {render: () => null}}],
})
const pushMock = vi.spyOn(router, "push").mockImplementation(() => Promise.resolve())

const mockMiscStore = {contextInfoBarOpenTab: "notifications"}
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => mockMiscStore,
}))

const resolveNotificationRouteMock = vi.fn()
const isNotificationLinkableMock = vi.fn()
vi.mock("../../../src/utils/notificationRoute", () => ({
    resolveNotificationRoute: (notification: Notification) => resolveNotificationRouteMock(notification),
    isNotificationLinkable: (type: string) => isNotificationLinkableMock(type),
}))

const mockNotifications = {value: [] as Notification[]}
const mockUnreadCount = {value: 0}
const markRead = vi.fn()
const markUnread = vi.fn()
const markAllRead = vi.fn()
const loadInitial = vi.fn().mockResolvedValue(undefined)
const loadMore = vi.fn().mockResolvedValue(undefined)
vi.mock("../../../src/stores/notifications", () => ({
    useNotificationsStore: () => ({
        get notifications() { return mockNotifications.value },
        get unreadCount() { return mockUnreadCount.value },
        get ongoingOperations() { return mockNotifications.value.filter(n => n.ongoing === true) },
        get staticNotifications() { return mockNotifications.value.filter(n => n.ongoing !== true) },
        get hasOngoingOperation() { return mockNotifications.value.some(n => n.ongoing === true) },
        get hasUnreadNotification() { return mockNotifications.value.some(n => !n.read) },
        markRead,
        markUnread,
        markAllRead,
        loadInitial,
        loadMore,
    }),
}))

import ContextNotifications from "../../../src/components/layout/ContextNotifications.vue"

const messages = {
    notifications: {
        heading: "Notifications",
        ongoing: "In progress",
        markAllRead: "Mark all as read",
        markUnread: "Mark as unread",
        markRead: "Mark as read",
        empty: "No notifications yet",
        emptyUnread: "No unread notifications",
        tabs: {
            all: "All",
            unread: "Unread",
        },
    },
}

function buildNotification(overrides: Partial<Omit<Notification, "type">> & {type?: string} = {}): Notification {
    return {
        id: "notif-1",
        userId: "user-1",
        tenantId: null,
        type: "GENERIC",
        title: "Something happened",
        referenceId: null,
        current: null,
        total: null,
        read: false,
        createdDate: "2024-01-01T00:00:00Z",
        updatedDate: "2024-01-01T00:00:00Z",
        ...overrides,
    } as Notification
}

// KsSegmented isn't registered in the unit env (see tests/unit/setup.ts) and an unresolved
// component can't drive a real v-model, so it's stubbed with a functional template wiring
// modelValue/update:modelValue to plain buttons — same pattern as the KsInput stub in
// WorkerDeleteDialog.spec.ts.
const stubs = {
    KsSegmented: {
        props: ["modelValue", "options"],
        emits: ["update:modelValue"],
        template: "<div><button v-for=\"opt in options\" :key=\"opt.value\" :data-value=\"opt.value\" @click=\"$emit('update:modelValue', opt.value)\">{{ opt.label }}</button></div>",
    },
}

// Most tests below only ever call mountComponent() and let the wrapper fall out of scope
// without unmounting. Track and unmount everything here instead of touching every
// individual test.
let mountedWrappers: Array<{unmount: () => void}> = []

function mountComponent() {
    const wrapper = i18nMount(ContextNotifications, {
        messages,
        global: {plugins: [router, createPinia()], stubs},
    })
    mountedWrappers.push(wrapper)
    return wrapper
}

// DynamicScroller recycles its pooled item-view nodes: once mounted, shrinking the item
// count leaves the now-unused pool slot in the DOM (position: absolute, visibility: hidden)
// rather than removing it — a plain ".row" count would double-count that leftover node.
function visibleRows(wrapper: ReturnType<typeof mountComponent>) {
    return wrapper
        .findAll(".vue-recycle-scroller__item-view")
        .filter((view) => !(view.attributes("style") ?? "").includes("visibility: hidden"))
        .flatMap((view) => view.findAll(".row"))
}

describe("ContextNotifications", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        mockNotifications.value = []
        mockUnreadCount.value = 0
        mockMiscStore.contextInfoBarOpenTab = "notifications"
        resolveNotificationRouteMock.mockReset().mockReturnValue(null)
        isNotificationLinkableMock.mockReset().mockReturnValue(false)
        pushMock.mockClear()
        markRead.mockClear()
        markUnread.mockClear()
        markAllRead.mockClear()
        loadInitial.mockClear()
    })

    afterEach(() => {
        mountedWrappers.forEach(w => w.unmount())
        mountedWrappers = []
    })

    it("loads history on mount", async () => {
        const wrapper = mountComponent()
        await wrapper.vm.$nextTick()

        expect(loadInitial).toHaveBeenCalledTimes(1)
    })

    it("shows the empty state when there are no notifications", () => {
        const wrapper = mountComponent()

        expect(wrapper.text()).toContain("No notifications yet")
    })

    it("renders one row per notification", () => {
        mockNotifications.value = [buildNotification({id: "a"}), buildNotification({id: "b"})]
        const wrapper = mountComponent()

        expect(wrapper.findAll(".row")).toHaveLength(2)
    })

    it("renders ongoing notifications in their own section, separate from the main list", () => {
        mockNotifications.value = [
            buildNotification({id: "a", ongoing: true, title: "Backfill running"}),
            buildNotification({id: "b", ongoing: false, title: "Backfill finished"}),
        ]
        const wrapper = mountComponent()

        expect(wrapper.find(".ongoingSection").text()).toContain("Backfill running")
        expect(wrapper.find(".ongoingSection").text()).not.toContain("Backfill finished")
        expect(visibleRows(wrapper).some(row => row.text().includes("Backfill running"))).toBe(false)
    })

    it("hides the ongoing section entirely when nothing is ongoing", () => {
        mockNotifications.value = [buildNotification({id: "a", ongoing: false})]
        const wrapper = mountComponent()

        expect(wrapper.find(".ongoingSection").exists()).toBe(false)
    })

    it("falls back to the BellOutline icon for a type with no registered icon", () => {
        mockNotifications.value = [buildNotification({id: "a", type: "SOME_FUTURE_TYPE"})]
        const genericWrapper = mountComponent()

        mockNotifications.value = [buildNotification({id: "a", type: "GENERIC"})]
        const wrapper = mountComponent()

        expect(wrapper.find(".icon").html()).toBe(genericWrapper.find(".icon").html())
    })

    it("hides the mark-all-read button when there is nothing unread", () => {
        mockUnreadCount.value = 0
        const wrapper = mountComponent()

        expect(wrapper.text()).not.toContain("Mark all as read")
    })

    it("shows the mark-all-read button and wires it up when there is unread content", async () => {
        mockUnreadCount.value = 2
        const wrapper = mountComponent()

        expect(wrapper.text()).toContain("Mark all as read")
        await wrapper.find(".notificationsHeader button").trigger("click")

        expect(markAllRead).toHaveBeenCalledTimes(1)
    })

    it("marks an unread row as read on click", async () => {
        mockNotifications.value = [buildNotification({id: "a", read: false})]
        const wrapper = mountComponent()

        await wrapper.find(".row").trigger("click")

        expect(markRead).toHaveBeenCalledWith("a")
    })

    it("does not re-mark an already-read row as read", async () => {
        mockNotifications.value = [buildNotification({id: "a", read: true})]
        const wrapper = mountComponent()

        await wrapper.find(".row").trigger("click")

        expect(markRead).not.toHaveBeenCalled()
    })

    it("only shows the mark-unread button on a read row, not an unread one", () => {
        mockNotifications.value = [
            buildNotification({id: "a", read: true}),
            buildNotification({id: "b", read: false}),
        ]
        const wrapper = mountComponent()
        const rows = wrapper.findAll(".row")

        expect(rows[0].find(".markUnreadBtn").exists()).toBe(true)
        expect(rows[1].find(".markUnreadBtn").exists()).toBe(false)
    })

    it("marks a read row as unread on button click, without also marking it read", async () => {
        mockNotifications.value = [buildNotification({id: "a", read: true})]
        const wrapper = mountComponent()

        await wrapper.find(".markUnreadBtn").trigger("click")

        expect(markUnread).toHaveBeenCalledWith("a")
        expect(markRead).not.toHaveBeenCalled()
    })

    it("only shows the mark-read button on an unread row, not a read one", () => {
        mockNotifications.value = [
            buildNotification({id: "a", read: true}),
            buildNotification({id: "b", read: false}),
        ]
        const wrapper = mountComponent()
        const rows = wrapper.findAll(".row")

        expect(rows[0].find(".markReadBtn").exists()).toBe(false)
        expect(rows[1].find(".markReadBtn").exists()).toBe(true)
    })

    it("marks an unread row as read via its button, without navigating", async () => {
        resolveNotificationRouteMock.mockReturnValue({name: "cases/view", params: {caseId: "case-1"}})
        mockNotifications.value = [buildNotification({id: "a", referenceId: "case-1", read: false})]
        const wrapper = mountComponent()

        await wrapper.find(".markReadBtn").trigger("click")

        expect(markRead).toHaveBeenCalledWith("a")
        expect(pushMock).not.toHaveBeenCalled()
    })

    it("does not render a reference link when no route resolves for the notification (today's default: every type)", () => {
        resolveNotificationRouteMock.mockReturnValue(null)
        mockNotifications.value = [buildNotification({id: "a", referenceId: null})]
        const wrapper = mountComponent()

        expect(wrapper.find(".reference-link").exists()).toBe(false)
        expect(wrapper.find(".reference-id-inert").exists()).toBe(false)
    })

    it("renders the referenceId inert (no link) when the notification's type is not linkable", () => {
        isNotificationLinkableMock.mockReturnValue(false)
        mockNotifications.value = [buildNotification({id: "a", referenceId: "case-1"})]
        const wrapper = mountComponent()

        expect(wrapper.find(".reference-id-inert").exists()).toBe(true)
        expect(wrapper.find(".reference-link").exists()).toBe(false)
    })

    it("renders the referenceId as a link when its type is linkable", () => {
        isNotificationLinkableMock.mockReturnValue(true)
        mockNotifications.value = [buildNotification({id: "a", referenceId: "case-1"})]
        const wrapper = mountComponent()

        expect(wrapper.find(".reference-link").exists()).toBe(true)
        expect(wrapper.find(".reference-id-inert").exists()).toBe(false)
    })

    it("clicking anywhere on the row marks it read and navigates when a route resolves", async () => {
        resolveNotificationRouteMock.mockReturnValue({name: "cases/view", params: {caseId: "case-1"}})
        mockNotifications.value = [buildNotification({id: "a", referenceId: "case-1", read: false})]
        const wrapper = mountComponent()

        await wrapper.find(".row").trigger("click")
        await wrapper.vm.$nextTick()

        expect(markRead).toHaveBeenCalledWith("a")
        expect(pushMock).toHaveBeenCalledWith({name: "cases/view", params: {tenant: undefined, caseId: "case-1"}})
        expect(mockMiscStore.contextInfoBarOpenTab).toBe("")
    })

    it("navigates on click even when the notification is already read", async () => {
        resolveNotificationRouteMock.mockReturnValue({name: "cases/view", params: {caseId: "case-1"}})
        mockNotifications.value = [buildNotification({id: "a", referenceId: "case-1", read: true})]
        const wrapper = mountComponent()

        await wrapper.find(".row").trigger("click")
        await wrapper.vm.$nextTick()

        expect(markRead).not.toHaveBeenCalled()
        expect(pushMock).toHaveBeenCalledWith({name: "cases/view", params: {tenant: undefined, caseId: "case-1"}})
    })

    it("does not close the panel when clicking a row with no resolvable route", async () => {
        resolveNotificationRouteMock.mockReturnValue(null)
        mockNotifications.value = [buildNotification({id: "a", read: false})]
        const wrapper = mountComponent()

        await wrapper.find(".row").trigger("click")
        await wrapper.vm.$nextTick()

        expect(pushMock).not.toHaveBeenCalled()
        expect(mockMiscStore.contextInfoBarOpenTab).toBe("notifications")
    })

    it("does not navigate on click when no route resolves for the notification", async () => {
        resolveNotificationRouteMock.mockReturnValue(null)
        mockNotifications.value = [buildNotification({id: "a", referenceId: null, read: false})]
        const wrapper = mountComponent()

        await wrapper.find(".row").trigger("click")

        expect(markRead).toHaveBeenCalledWith("a")
        expect(pushMock).not.toHaveBeenCalled()
    })

    it("marking unread via its button does not also navigate", async () => {
        resolveNotificationRouteMock.mockReturnValue({name: "cases/view", params: {caseId: "case-1"}})
        mockNotifications.value = [buildNotification({id: "a", referenceId: "case-1", read: true})]
        const wrapper = mountComponent()

        await wrapper.find(".markUnreadBtn").trigger("click")

        expect(pushMock).not.toHaveBeenCalled()
    })

    it("shows every notification on the All tab by default, in the order the store provides", () => {
        mockNotifications.value = [
            buildNotification({id: "a", read: true, title: "Read one"}),
            buildNotification({id: "b", read: false, title: "Unread one"}),
        ]
        const wrapper = mountComponent()

        expect(wrapper.findAll(".row")).toHaveLength(2)
    })

    it("filters to only unread notifications on the Unread tab", async () => {
        mockNotifications.value = [
            buildNotification({id: "a", read: true, title: "Read one"}),
            buildNotification({id: "b", read: false, title: "Unread one"}),
        ]
        const wrapper = mountComponent()

        await wrapper.find("[data-value=\"unread\"]").trigger("click")
        await wrapper.vm.$nextTick()

        const rows = visibleRows(wrapper)
        expect(rows).toHaveLength(1)
        expect(rows[0].text()).toContain("Unread one")
    })

    it("switching back to All restores the full list", async () => {
        mockNotifications.value = [
            buildNotification({id: "a", read: true}),
            buildNotification({id: "b", read: false}),
        ]
        const wrapper = mountComponent()

        await wrapper.find("[data-value=\"unread\"]").trigger("click")
        await wrapper.vm.$nextTick()
        expect(visibleRows(wrapper)).toHaveLength(1)

        await wrapper.find("[data-value=\"all\"]").trigger("click")
        await wrapper.vm.$nextTick()
        expect(visibleRows(wrapper)).toHaveLength(2)
    })

    it("shows an unread-specific empty state on the Unread tab when nothing is unread", async () => {
        mockNotifications.value = [buildNotification({id: "a", read: true})]
        const wrapper = mountComponent()

        await wrapper.find("[data-value=\"unread\"]").trigger("click")

        expect(wrapper.text()).toContain("No unread notifications")
        expect(wrapper.text()).not.toContain("No notifications yet")
    })

    it("does not mark a notification read just because the panel was unmounted (closed)", async () => {
        mockNotifications.value = [buildNotification({id: "a", read: false})]
        const wrapper = mountComponent()
        await wrapper.vm.$nextTick()

        wrapper.unmount()

        expect(markRead).not.toHaveBeenCalled()
    })

    it("leaving the panel does not silently re-mark an explicitly-unread notification as read", async () => {
        mockNotifications.value = [buildNotification({id: "a", read: true})]
        const wrapper = mountComponent()

        await wrapper.find(".markUnreadBtn").trigger("click")
        expect(markUnread).toHaveBeenCalledWith("a")

        wrapper.unmount()

        expect(markRead).not.toHaveBeenCalled()
    })
})
