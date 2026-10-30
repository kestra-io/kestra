import {beforeEach, describe, expect, it, vi} from "vitest"
import {i18nMount} from "../../../tests/unit/i18nMount"

const mocks = vi.hoisted(() => ({
    flow: {
        deleted: false,
        draft: false,
        namespace: "company.team",
    },
    isAllowed: vi.fn(),
}))

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({flow: mocks.flow}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {isAllowed: mocks.isAllowed},
    }),
}))

vi.mock("override/components/flows/Actions.vue", () => ({
    default: {
        template: "<div class=\"actions\" />",
    },
}))

import FlowRootTopBar from "./FlowRootTopBar.vue"

const routeInfo = {
    title: "My Flow",
    breadcrumb: [{label: "Flows", path: "/flows"}],
    bookmarkLabel: "My bookmark",
}

function mountFlowRootTopBar() {
    return i18nMount(FlowRootTopBar, {
        props: {routeInfo},
        global: {
            stubs: {
                NavBar: {
                    props: ["breadcrumb", "title", "bookmarkLabel"],
                    template: `
                        <div
                            class="nav-bar"
                            :data-title="title"
                            :data-bookmark-label="bookmarkLabel"
                            :data-breadcrumb="JSON.stringify(breadcrumb)"
                        >
                            <div class="nav-title"><slot name="title" /></div>
                            <div class="nav-actions"><slot name="actions" /></div>
                        </div>
                    `,
                },
                Badge: {
                    props: ["label"],
                    template: "<span class=\"badge\">{{ label }}</span>",
                },
                Alert: {
                    template: "<span class=\"alert-icon\" />",
                },
                Lock: {
                    template: "<span class=\"lock-icon\" />",
                },
            },
        },
    })
}

describe("FlowRootTopBar", () => {
    beforeEach(() => {
        mocks.flow = {
            deleted: false,
            draft: false,
            namespace: "company.team",
        }
        mocks.isAllowed.mockReset().mockReturnValue(true)
    })

    it("shows the deleted label for deleted flows", () => {
        mocks.flow.deleted = true

        const wrapper = mountFlowRootTopBar()

        expect(wrapper.text()).toContain("deleted_label")
        expect(wrapper.find(".alert-icon").exists()).toBe(true)
    })

    it("does not show the deleted label for flows that are not deleted", () => {
        const wrapper = mountFlowRootTopBar()

        expect(wrapper.text()).not.toContain("deleted_label")
        expect(wrapper.find(".alert-icon").exists()).toBe(false)
    })

    it("shows the draft badge for draft flows", () => {
        mocks.flow.draft = true

        const wrapper = mountFlowRootTopBar()

        expect(wrapper.findAll(".badge").map(badge => badge.text())).toContain("draft")
    })

    it("does not show the draft badge for flows that are not drafts", () => {
        const wrapper = mountFlowRootTopBar()

        expect(wrapper.findAll(".badge").map(badge => badge.text())).not.toContain("draft")
    })

    it("shows the deleted label and draft badge for deleted draft flows", () => {
        mocks.flow.deleted = true
        mocks.flow.draft = true

        const wrapper = mountFlowRootTopBar()

        expect(wrapper.text()).toContain("deleted_label")
        expect(wrapper.find(".alert-icon").exists()).toBe(true)
        expect(wrapper.findAll(".badge").map(badge => badge.text())).toContain("draft")
    })

    it("shows the lock icon when the user cannot edit the flow", () => {
        mocks.isAllowed.mockReturnValue(false)

        const wrapper = mountFlowRootTopBar()

        expect(wrapper.find(".lock-icon").exists()).toBe(true)
    })

    it("does not show the lock icon when the user can edit the flow", () => {
        const wrapper = mountFlowRootTopBar()

        expect(wrapper.find(".lock-icon").exists()).toBe(false)
    })

    it("shows the deleted label instead of the lock icon for a deleted flow the user cannot edit", () => {
        mocks.flow.deleted = true
        mocks.isAllowed.mockReturnValue(false)

        const wrapper = mountFlowRootTopBar()

        expect(wrapper.find(".alert-icon").exists()).toBe(true)
        expect(wrapper.find(".lock-icon").exists()).toBe(false)
    })

    it("checks update permission for the flow namespace", () => {
        mountFlowRootTopBar()

        expect(mocks.isAllowed).toHaveBeenCalledWith("FLOW", "UPDATE", "company.team")
    })

    it("renders the flow actions in the navigation bar", () => {
        const wrapper = mountFlowRootTopBar()

        expect(wrapper.find(".nav-actions .actions").exists()).toBe(true)
    })

    it("passes the route title, breadcrumb, and bookmark label to the navigation bar", () => {
        const wrapper = mountFlowRootTopBar()
        const navBar = wrapper.find(".nav-bar")

        expect(navBar.attributes("data-title")).toBe("My Flow")
        expect(navBar.attributes("data-bookmark-label")).toBe("My bookmark")
        expect(JSON.parse(navBar.attributes("data-breadcrumb")!)).toEqual(routeInfo.breadcrumb)
    })
})
