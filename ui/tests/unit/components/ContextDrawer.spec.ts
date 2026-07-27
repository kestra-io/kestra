import {describe, it, expect, beforeEach, vi} from "vitest"
import {mount} from "@vue/test-utils"

const StubIcon = {template: "<span />"}

const mockButtons = {
    news: {title: "News", icon: StubIcon, hasUnreadMarker: false},
    // Mirrors the EE notifications button: resolvable for panel content, but excluded
    // from the visible tab strip because it has its own dedicated bell entry point.
    notifications: {title: "Notifications", icon: StubIcon, hasUnreadMarker: true, unread: {value: false}, hidden: true},
}
vi.mock("override/composables/contextButtons", () => ({
    useContextButtons: () => ({buttons: mockButtons}),
}))

let mockOpenTab = "news"
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({
        get contextInfoBarOpenTab() { return mockOpenTab },
        set contextInfoBarOpenTab(value: string) { mockOpenTab = value },
        lastContextTab: "news",
    }),
}))

import ContextDrawer from "../../../src/components/ContextDrawer.vue"

function mountComponent() {
    return mount(ContextDrawer)
}

describe("ContextDrawer", () => {
    beforeEach(() => {
        mockOpenTab = "news"
    })

    it("excludes hidden buttons from the visible tab strip", () => {
        const wrapper = mountComponent()

        expect(wrapper.html()).toContain("name=\"news\"")
        expect(wrapper.html()).not.toContain("name=\"notifications\"")
    })

    it("shows the tab strip when a visible tab is active", () => {
        const wrapper = mountComponent()

        expect(wrapper.find(".tabBar").exists()).toBe(true)
    })

    it("hides the tab strip entirely when the notifications pane is active", () => {
        mockOpenTab = "notifications"
        const wrapper = mountComponent()

        expect(wrapper.find(".tabBar").exists()).toBe(false)
    })
})
