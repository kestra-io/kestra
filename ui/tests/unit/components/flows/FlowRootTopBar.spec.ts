import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import FlowRootTopBar from "../../../../src/components/flows/FlowRootTopBar.vue"

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        flow: {deleted: true, id: "my-flow", namespace: "company.team"}
    })
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {isAllowed: () => true}
    })
}))

vi.mock("override/components/flows/Actions.vue", () => ({
    default: {template: "<div></div>"}
}))

describe("FlowRootTopBar", () => {
    it("renders the deleted label with spacing class when flow is deleted", () => {
        const wrapper = mount(FlowRootTopBar, {
            props: {
                routeInfo: {
                    title: "my-flow",
                    breadcrumb: []
                }
            },
            global: {
                mocks: {
                    $t: (key: string) => (key === "deleted_label" ? "Deleted" : key)
                },
                stubs: {
                    NavBar: {
                        template: "<header><slot name='title' /><slot name='actions' /></header>"
                    },
                    Alert: true,
                    Badge: true
                }
            }
        })

        const deletedSpan = wrapper.find(".me-1")
        expect(deletedSpan.exists()).toBe(true)
        expect(deletedSpan.text()).toBe("Deleted")
    })
})