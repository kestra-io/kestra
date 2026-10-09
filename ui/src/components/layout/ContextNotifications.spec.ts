import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {createRouter, createWebHistory} from "vue-router"
import ContextNotifications from "./ContextNotifications.vue"
import type {Notification} from "../../stores/notifications"
import {i18nMount} from "../../../tests/unit/i18nMount"

const store = vi.hoisted(() => ({
    notifications: [] as Notification[],
    unreadCount: 0,
    markRead: vi.fn(),
    markUnread: vi.fn(),
    markAllRead: vi.fn(),
    loadInitial: vi.fn(),
    loadMore: vi.fn(),
}))
const miscStore = vi.hoisted(() => ({contextInfoBarOpenTab: "notifications"}))
const routes = vi.hoisted(() => ({resolve: vi.fn(), isLinkable: vi.fn()}))

vi.mock("../../stores/notifications", () => ({useNotificationsStore: () => store}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => miscStore}))
vi.mock("../../utils/notificationRoute", () => ({
    resolveNotificationRoute: routes.resolve,
    isNotificationLinkable: routes.isLinkable,
}))

const router = createRouter({
    history: createWebHistory(),
    routes: [{path: "/mock/:tenant?/:caseId?", name: "cases/view", component: {render: () => null}}],
})
const push = vi.spyOn(router, "push").mockResolvedValue(undefined)

const caseRoute = {name: "cases/view", params: {caseId: "case-1"}}
const pushedCaseRoute = {name: "cases/view", params: {tenant: undefined, caseId: "case-1"}}

const messages = {
    notifications: {
        markAllRead: "Mark all as read",
        empty: "No notifications yet",
        emptyUnread: "No unread notifications",
    },
}

const stubs = {
    KsTabs: {
        props: ["modelValue"],
        emits: ["update:modelValue"],
        template: "<div><slot /></div>",
    },
    KsTabPane: {
        props: ["name"],
        template: "<button :data-value=\"name\" @click=\"$parent.$emit('update:modelValue', name)\" />",
    },
}

function notification(overrides: Partial<Notification> = {}): Notification {
    return {
        id: "a",
        userId: "user-1",
        tenantId: null,
        type: "GENERIC",
        title: "Something happened",
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

function mount() {
    return i18nMount(ContextNotifications, {messages, global: {plugins: [router], stubs}})
}

/** DynamicScroller keeps recycled item views in the DOM, hidden, so only the visible ones count. */
function visibleRows(wrapper: VueWrapper) {
    return wrapper
        .findAll(".vue-recycle-scroller__item-view")
        .filter(view => !(view.attributes("style") ?? "").includes("visibility: hidden"))
        .flatMap(view => view.findAll(".row"))
}

async function showTab(wrapper: VueWrapper, tab: "all" | "unread") {
    await wrapper.find(`[data-value='${tab}']`).trigger("click")
    await flushPromises()
}

describe("ContextNotifications", () => {
    beforeEach(() => {
        store.notifications = []
        store.unreadCount = 0
        store.markRead.mockReset().mockResolvedValue(undefined)
        store.markUnread.mockReset().mockResolvedValue(undefined)
        store.markAllRead.mockReset()
        store.loadInitial.mockReset().mockResolvedValue(undefined)
        miscStore.contextInfoBarOpenTab = "notifications"
        routes.resolve.mockReset().mockReturnValue(null)
        routes.isLinkable.mockReset().mockReturnValue(false)
        push.mockClear()
    })

    it("loads history on mount", () => {
        mount()

        expect(store.loadInitial).toHaveBeenCalledTimes(1)
    })

    it("shows the empty state when there are no notifications", () => {
        expect(mount().text()).toContain("No notifications yet")
    })

    it("lists every notification on the All tab", () => {
        store.notifications = [notification({id: "a", read: true}), notification({id: "b"})]

        expect(mount().findAll(".row")).toHaveLength(2)
    })

    it("lists ongoing operations in their own section, out of the main list", () => {
        store.notifications = [
            notification({id: "a", ongoing: true, title: "Backfill running"}),
            notification({id: "b", ongoing: false, title: "Backfill finished"}),
        ]

        const wrapper = mount()

        expect(wrapper.find(".ongoing").text()).toContain("Backfill running")
        expect(wrapper.find(".ongoing").text()).not.toContain("Backfill finished")
        expect(visibleRows(wrapper).some(row => row.text().includes("Backfill running"))).toBe(false)
    })

    it("hides the ongoing section when nothing is ongoing", () => {
        store.notifications = [notification({ongoing: false})]

        expect(mount().find(".ongoing").exists()).toBe(false)
    })

    it("marks an ongoing operation read and opens its target when its card is clicked", async () => {
        const query = {"filters[operationId][EQUALS]": "op-1"}
        store.notifications = [notification({id: "op", type: "ASYNC_OPERATION", ongoing: true, referenceId: "op-1"})]
        routes.resolve.mockReturnValue({name: "executions/list", params: {}, query})

        await mount().find("[data-test='operation-card']").trigger("click")
        await flushPromises()

        expect(store.markRead).toHaveBeenCalledWith("op")
        expect(push).toHaveBeenCalledWith({name: "executions/list", params: {tenant: undefined}, query})
    })

    it("falls back to the generic icon for a type with no registered icon", () => {
        store.notifications = [notification({type: "SOME_FUTURE_TYPE"})]
        const unknown = mount().find(".icon").html()

        store.notifications = [notification({type: "GENERIC"})]

        expect(mount().find(".icon").html()).toBe(unknown)
    })

    it("hides mark-all-read when there is nothing unread", () => {
        expect(mount().text()).not.toContain("Mark all as read")
    })

    it("marks everything read from the header", async () => {
        store.unreadCount = 2

        await mount().find(".header button").trigger("click")

        expect(store.markAllRead).toHaveBeenCalledTimes(1)
    })

    it("offers mark-unread on a read row and mark-read on an unread one", () => {
        store.notifications = [notification({id: "a", read: true}), notification({id: "b", read: false})]

        const [read, unread] = mount().findAll(".row")

        expect(read.find("[data-test='mark-unread']").exists()).toBe(true)
        expect(read.find("[data-test='mark-read']").exists()).toBe(false)
        expect(unread.find("[data-test='mark-read']").exists()).toBe(true)
        expect(unread.find("[data-test='mark-unread']").exists()).toBe(false)
    })

    it("marks a row read from its button without navigating", async () => {
        routes.resolve.mockReturnValue(caseRoute)
        store.notifications = [notification({referenceId: "case-1"})]

        await mount().find("[data-test='mark-read']").trigger("click")
        await flushPromises()

        expect(store.markRead).toHaveBeenCalledWith("a")
        expect(push).not.toHaveBeenCalled()
    })

    it("marks a row unread from its button without navigating, and closing the panel keeps it unread", async () => {
        routes.resolve.mockReturnValue(caseRoute)
        store.notifications = [notification({referenceId: "case-1", read: true})]
        const wrapper = mount()

        await wrapper.find("[data-test='mark-unread']").trigger("click")
        await flushPromises()
        wrapper.unmount()

        expect(store.markUnread).toHaveBeenCalledWith("a")
        expect(store.markRead).not.toHaveBeenCalled()
        expect(push).not.toHaveBeenCalled()
    })

    it("does not mark anything read just because the panel closed", () => {
        store.notifications = [notification()]

        mount().unmount()

        expect(store.markRead).not.toHaveBeenCalled()
    })

    it("renders no reference without a referenceId", () => {
        expect(mount().find("[data-test='reference']").exists()).toBe(false)
    })

    it.each([true, false])("styles the reference as a link only when its type is linkable (linkable: %s)", linkable => {
        routes.isLinkable.mockReturnValue(linkable)
        store.notifications = [notification({referenceId: "case-1"})]

        expect(mount().find("[data-test='reference']").classes().includes("link")).toBe(linkable)
    })

    it("marks an unread row read, closes the panel and navigates when it is clicked", async () => {
        routes.resolve.mockReturnValue(caseRoute)
        store.notifications = [notification({referenceId: "case-1"})]

        await mount().find(".row").trigger("click")
        await flushPromises()

        expect(store.markRead).toHaveBeenCalledWith("a")
        expect(push).toHaveBeenCalledWith(pushedCaseRoute)
        expect(miscStore.contextInfoBarOpenTab).toBe("")
    })

    it("navigates from a read row without marking it again", async () => {
        routes.resolve.mockReturnValue(caseRoute)
        store.notifications = [notification({referenceId: "case-1", read: true})]

        await mount().find(".row").trigger("click")
        await flushPromises()

        expect(store.markRead).not.toHaveBeenCalled()
        expect(push).toHaveBeenCalledWith(pushedCaseRoute)
    })

    it("only marks a row read when nothing resolves for it, keeping the panel open", async () => {
        store.notifications = [notification()]

        await mount().find(".row").trigger("click")
        await flushPromises()

        expect(store.markRead).toHaveBeenCalledWith("a")
        expect(push).not.toHaveBeenCalled()
        expect(miscStore.contextInfoBarOpenTab).toBe("notifications")
    })

    it("switches between unread and all notifications", async () => {
        store.notifications = [
            notification({id: "a", read: true, title: "Read one"}),
            notification({id: "b", read: false, title: "Unread one"}),
        ]
        const wrapper = mount()

        await showTab(wrapper, "unread")
        expect(visibleRows(wrapper).map(row => row.text())).toEqual([expect.stringContaining("Unread one")])

        await showTab(wrapper, "all")
        expect(visibleRows(wrapper)).toHaveLength(2)
    })

    it("shows an unread-specific empty state on the Unread tab", async () => {
        store.notifications = [notification({read: true})]
        const wrapper = mount()

        await showTab(wrapper, "unread")

        expect(wrapper.text()).toContain("No unread notifications")
        expect(wrapper.text()).not.toContain("No notifications yet")
    })
})
