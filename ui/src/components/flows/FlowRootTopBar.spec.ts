import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"

let mockFlow: Record<string, any> | undefined
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({
        get flow() {
            return mockFlow
        },
    }),
}))

let mockUser: {isAllowed: () => boolean} | undefined
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        get user() {
            return mockUser
        },
    }),
}))

vi.mock("override/components/flows/Actions.vue", () => ({
    default: {template: "<div class=\"actions\" />"},
}))

import FlowRootTopBar from "./FlowRootTopBar.vue"

const stubs = {
    NavBar: {
        props: ["breadcrumb", "title", "bookmarkLabel"],
        template: `
            <div class="top-nav-bar">
                <div class="title-slot"><slot name="title" /></div>
                <div class="actions-slot"><slot name="actions" /></div>
            </div>
        `,
    },
    Alert: {template: "<span class=\"alert-icon\" />"},
    Lock: {template: "<span class=\"lock-icon\" />"},
    Badge: {props: ["label"], template: "<span class=\"badge\">{{ label }}</span>"},
}

function mountComponent(props = {}) {
    return mount(FlowRootTopBar, {
        props: {
            routeInfo: {
                title: "my-flow",
                breadcrumb: [],
            },
            ...props,
        },
        global: {
            stubs,
            mocks: {
                $t: (key: string) => (key === "deleted_label" ? "Deleted" : key),
            },
        },
    })
}

describe("FlowRootTopBar", () => {
    beforeEach(() => {
        mockFlow = undefined
        mockUser = {isAllowed: () => true}
    })

    it("renders title with Deleted prefix and space when flow is deleted", () => {
        mockFlow = {namespace: "company.team", id: "my-flow", deleted: true}
        const wrapper = mountComponent()
        const titleSlot = wrapper.find(".title-slot")
        expect(titleSlot.find(".alert-icon").exists()).toBe(true)
        // Verify there is a space between 'Deleted' and the flow name (no 'Deletedmy-flow')
        expect(titleSlot.text()).toContain("Deleted")
        expect(titleSlot.text()).toContain("my-flow")
        expect(titleSlot.text()).not.toContain("Deletedmy-flow")
    })

    it("renders title without Deleted prefix when flow is not deleted", () => {
        mockFlow = {namespace: "company.team", id: "my-flow", deleted: false}
        const wrapper = mountComponent()
        const titleSlot = wrapper.find(".title-slot")
        expect(titleSlot.find(".alert-icon").exists()).toBe(false)
        expect(titleSlot.text()).toBe("my-flow")
    })

    it("shows lock icon when user is not allowed to edit", () => {
        mockFlow = {namespace: "company.team", id: "my-flow", deleted: false}
        mockUser = {isAllowed: () => false}
        const wrapper = mountComponent()
        const titleSlot = wrapper.find(".title-slot")
        expect(titleSlot.find(".lock-icon").exists()).toBe(true)
    })
})
