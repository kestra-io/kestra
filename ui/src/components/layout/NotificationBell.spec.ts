import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import NotificationBell from "./NotificationBell.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const store = vi.hoisted(() => ({
    unreadCount: 0,
    notifications: [] as {ongoing?: boolean}[],
    startSSE: vi.fn(),
    stopSSE: vi.fn(),
}))
const miscStore = vi.hoisted(() => ({contextInfoBarOpenTab: "", lastContextTab: "news"}))

vi.mock("../../stores/notifications", () => ({useNotificationsStore: () => store}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => miscStore}))

const messages = {
    notifications: {
        bellAriaLabel: "Notifications",
        bellAriaLabelRunning: "Notifications, operations in progress",
    },
}

function mount() {
    return i18nMount(NotificationBell, {messages, attachTo: document.body})
}

function element(className = "") {
    const el = document.createElement("div")
    el.className = className
    document.body.appendChild(el)
    return el
}

function click(target: Element) {
    target.dispatchEvent(new MouseEvent("click", {bubbles: true}))
}

describe("NotificationBell", () => {
    beforeEach(() => {
        store.notifications = []
        store.startSSE.mockClear()
        store.stopSSE.mockClear()
        miscStore.contextInfoBarOpenTab = ""
        miscStore.lastContextTab = "news"
    })

    afterEach(() => {
        document.body.replaceChildren()
    })

    it("follows notifications while mounted", () => {
        const wrapper = mount()
        expect(store.startSSE).toHaveBeenCalledTimes(1)

        wrapper.unmount()
        expect(store.stopSSE).toHaveBeenCalledTimes(1)
    })

    it("tells screen readers an operation is running, since the pulse alone is invisible to them", () => {
        store.notifications = [{ongoing: false}, {ongoing: true}]

        const bell = mount().find("[data-test='notification-bell']")

        expect(bell.attributes("aria-label")).toBe("Notifications, operations in progress")
    })

    it("toggles the panel without changing the tab the dock toggle reopens", async () => {
        const bell = mount().find("[data-test='notification-bell']")

        await bell.trigger("click")
        expect(miscStore.contextInfoBarOpenTab).toBe("notifications")

        await bell.trigger("click")
        expect(miscStore.contextInfoBarOpenTab).toBe("")
        expect(miscStore.lastContextTab).toBe("news")
    })

    it("closes the panel on a click outside it", () => {
        miscStore.contextInfoBarOpenTab = "notifications"
        mount()

        click(element())

        expect(miscStore.contextInfoBarOpenTab).toBe("")
    })

    it("keeps the panel open on a click inside the drawer", () => {
        miscStore.contextInfoBarOpenTab = "notifications"
        mount()

        click(element("contextDrawer"))

        expect(miscStore.contextInfoBarOpenTab).toBe("notifications")
    })

    it("keeps the panel open when the clicked row leaves the DOM mid-click", () => {
        miscStore.contextInfoBarOpenTab = "notifications"
        mount()
        const row = document.createElement("div")
        element("contextDrawer").appendChild(row)
        row.addEventListener("click", () => row.remove())

        click(row)

        expect(miscStore.contextInfoBarOpenTab).toBe("notifications")
    })

    it("leaves other panels alone on an outside click", () => {
        miscStore.contextInfoBarOpenTab = "news"
        mount()

        click(element())

        expect(miscStore.contextInfoBarOpenTab).toBe("news")
    })

    it("stops listening for outside clicks once unmounted", () => {
        miscStore.contextInfoBarOpenTab = "notifications"
        mount().unmount()

        click(element())

        expect(miscStore.contextInfoBarOpenTab).toBe("notifications")
    })
})
