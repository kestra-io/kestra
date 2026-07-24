import {describe, it, expect, beforeEach, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {mountGlobal} from "./_helpers"

// The page registers a route title on mount — no-op it, there's no router in the unit env.
vi.mock("../../../../../src/composables/useRouteContext", () => ({default: () => {}}))
// Drive the header action + the COPILOT permission gate from the test via the auth store.
let canCreateFlow = true
let canUseCopilot = true
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {hasAnyActionOnAnyNamespace: () => canCreateFlow, hasAny: () => canUseCopilot}}),
}))
// The page redirects users without COPILOT to their dashboards — capture the navigation.
const replace = vi.fn()
vi.mock("vue-router", () => ({
    useRoute: () => ({params: {tenant: "acme"}}),
    useRouter: () => ({replace}),
}))

import CopilotPage from "../../../../../src/components/ai/copilot/CopilotPage.vue"

// CopilotChat is exercised by its own spec — stub it here so the page test stays isolated
// and only asserts the wiring (layout, title, header action).
const CopilotChatStub = {name: "CopilotChat", props: ["layout"], template: "<div class=\"copilot-chat-stub\" :data-layout=\"layout\" />"}
const TopNavBarStub = {name: "TopNavBar", props: ["title"], template: "<div class=\"topnav\"><span class=\"topnav-title\">{{ title }}</span><slot name=\"actions\" /></div>"}
const NavBarActionsStub = {name: "NavBarActions", template: "<div class=\"navbar-actions\"><slot /></div>"}
const NavBarActionStub = {name: "NavBarAction", props: ["icon", "label", "to"], template: "<a class=\"navbar-action\" :data-to=\"JSON.stringify(to)\">{{ label }}</a>"}

const mountPage = () =>
    mount(CopilotPage, {
        global: {
            ...mountGlobal,
            stubs: {
                ...mountGlobal.stubs,
                CopilotChat: CopilotChatStub,
                TopNavBar: TopNavBarStub,
                NavBarActions: NavBarActionsStub,
                NavBarAction: NavBarActionStub,
            },
        },
    })

describe("CopilotPage", () => {
    beforeEach(() => {
        canCreateFlow = true
        canUseCopilot = true
        replace.mockReset()
    })

    it("hosts CopilotChat in the full-page layout under the AI Copilot title", () => {
        const w = mountPage()
        const chat = w.find(".copilot-chat-stub")
        expect(chat.exists()).toBe(true)
        expect(chat.attributes("data-layout")).toBe("page")
        expect(w.find(".topnav-title").text()).toBe("AI Copilot")
    })

    it("offers a 'Create flow from scratch' action pointing at the flow creation route when allowed", () => {
        const w = mountPage()
        const action = w.find(".navbar-action")
        expect(action.exists()).toBe(true)
        expect(action.text()).toBe("Create flow from scratch")
        expect(JSON.parse(action.attributes("data-to") ?? "{}")).toEqual({name: "flows/create"})
    })

    it("hides the create-flow action for a user without flow-creation permission", () => {
        canCreateFlow = false
        const w = mountPage()
        expect(w.find(".navbar-action").exists()).toBe(false)
    })

    it("renders the copilot for a user with the COPILOT permission (no redirect)", () => {
        const w = mountPage()
        expect(w.find(".copilot-chat-stub").exists()).toBe(true)
        expect(replace).not.toHaveBeenCalled()
    })

    it("redirects a user without the COPILOT permission to their dashboards", () => {
        canUseCopilot = false
        const w = mountPage()
        // The surface is not rendered, and the user is sent to the tenant's dashboards.
        expect(w.find(".copilot-chat-stub").exists()).toBe(false)
        expect(replace).toHaveBeenCalledWith({name: "home", params: {tenant: "acme"}})
    })
})
