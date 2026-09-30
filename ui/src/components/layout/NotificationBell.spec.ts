import {describe, it, expect, vi, beforeEach} from "vitest"
const mockUnreadCount = {value: 0}
const startSSE = vi.fn()
const stopSSE = vi.fn()
vi.mock("../../stores/notifications", () => ({
    useNotificationsStore: () => ({
        get unreadCount() { return mockUnreadCount.value },
        startSSE,
        stopSSE,
    }),
}))

const mockMiscStore = {contextInfoBarOpenTab: "", lastContextTab: "news"}
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => mockMiscStore,
}))

import NotificationBell from "./NotificationBell.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

function mountComponent(options: {attachTo?: HTMLElement} = {}) {
    return i18nMount(NotificationBell, {
        messages: {notifications: {bellAriaLabel: "Notifications"}},
        ...options,
    })
}

describe("NotificationBell", () => {
    beforeEach(() => {
        mockUnreadCount.value = 0
        mockMiscStore.contextInfoBarOpenTab = ""
        mockMiscStore.lastContextTab = "news"
        startSSE.mockClear()
        stopSSE.mockClear()
    })

    it("starts the SSE subscription on mount and stops it on unmount", () => {
        const wrapper = mountComponent()

        expect(startSSE).toHaveBeenCalledTimes(1)

        wrapper.unmount()

        expect(stopSSE).toHaveBeenCalledTimes(1)
    })

    it("hides the count badge when there is nothing unread", () => {
        mockUnreadCount.value = 0
        const wrapper = mountComponent()

        expect(wrapper.find(".kel-badge__content").exists()).toBe(false)

        wrapper.unmount()
    })

    it("shows the unread count on the badge", () => {
        mockUnreadCount.value = 3
        const wrapper = mountComponent()

        const badge = wrapper.find(".kel-badge__content")
        expect(badge.exists()).toBe(true)
        expect(badge.text()).toBe("3")

        wrapper.unmount()
    })

    it("opens the notifications panel on click, without hijacking the shared dock-toggle's last tab", async () => {
        const wrapper = mountComponent()

        await wrapper.find(".icon-btn").trigger("click")

        expect(mockMiscStore.contextInfoBarOpenTab).toBe("notifications")
        expect(mockMiscStore.lastContextTab).toBe("news")

        wrapper.unmount()
    })

    it("closes the panel on click when notifications is already the active tab", async () => {
        mockMiscStore.contextInfoBarOpenTab = "notifications"
        const wrapper = mountComponent()

        await wrapper.find(".icon-btn").trigger("click")

        expect(mockMiscStore.contextInfoBarOpenTab).toBe("")

        wrapper.unmount()
    })

    describe("click outside", () => {
        it("closes the panel when clicking outside the drawer and the bell", () => {
            mockMiscStore.contextInfoBarOpenTab = "notifications"
            const wrapper = mountComponent({attachTo: document.body})

            const outside = document.createElement("div")
            document.body.appendChild(outside)
            outside.dispatchEvent(new MouseEvent("click", {bubbles: true}))

            expect(mockMiscStore.contextInfoBarOpenTab).toBe("")

            outside.remove()
            wrapper.unmount()
        })

        it("keeps the panel open when clicking inside the context drawer", () => {
            mockMiscStore.contextInfoBarOpenTab = "notifications"
            const wrapper = mountComponent({attachTo: document.body})

            const drawer = document.createElement("div")
            drawer.className = "contextDrawer"
            document.body.appendChild(drawer)
            drawer.dispatchEvent(new MouseEvent("click", {bubbles: true}))

            expect(mockMiscStore.contextInfoBarOpenTab).toBe("notifications")

            drawer.remove()
            wrapper.unmount()
        })

        it("does not affect other open tabs", () => {
            mockMiscStore.contextInfoBarOpenTab = "news"
            const wrapper = mountComponent({attachTo: document.body})

            const outside = document.createElement("div")
            document.body.appendChild(outside)
            outside.dispatchEvent(new MouseEvent("click", {bubbles: true}))

            expect(mockMiscStore.contextInfoBarOpenTab).toBe("news")

            outside.remove()
            wrapper.unmount()
        })

        it("keeps the panel open when a row detaches from the DOM mid-click (e.g. filtered out of the Unread tab after being marked read)", () => {
            mockMiscStore.contextInfoBarOpenTab = "notifications"
            const wrapper = mountComponent({attachTo: document.body})

            const drawer = document.createElement("div")
            drawer.className = "contextDrawer"
            const row = document.createElement("div")
            drawer.appendChild(row)
            document.body.appendChild(drawer)

            row.addEventListener("click", () => row.remove())
            row.dispatchEvent(new MouseEvent("click", {bubbles: true}))

            expect(mockMiscStore.contextInfoBarOpenTab).toBe("notifications")

            drawer.remove()
            wrapper.unmount()
        })

        it("stops listening once unmounted", () => {
            mockMiscStore.contextInfoBarOpenTab = "notifications"
            const wrapper = mountComponent({attachTo: document.body})
            wrapper.unmount()

            const outside = document.createElement("div")
            document.body.appendChild(outside)
            outside.dispatchEvent(new MouseEvent("click", {bubbles: true}))

            expect(mockMiscStore.contextInfoBarOpenTab).toBe("notifications")

            outside.remove()
        })
    })
})
