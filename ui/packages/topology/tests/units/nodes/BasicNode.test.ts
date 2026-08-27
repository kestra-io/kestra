import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import BasicNode from "../../../src/nodes/BasicNode.vue"

function mountBasicNode() {
    return mount(BasicNode, {
        props: {
            id: "root.my-task",
            data: {
                node: {},
                color: "default",
                unused: false,
                parent: {},
            },
            icons: {},
        },
        global: {
            stubs: {
                // KsTooltip wraps the title in an element-plus popper; render only its default slot.
                KsTooltip: {template: "<span><slot /></span>"},
            },
        },
        slots: {
            badge: "<span class='badge-marker'>badge</span>",
            "title-status": "<span class='status-marker'>status</span>",
            "title-actions": "<span class='actions-marker'>actions</span>",
        },
    })
}

describe("BasicNode layout", () => {
    it("should render the badge above the title, outside the title row", () => {
        const wrapper = mountBasicNode()

        expect(wrapper.find(".node-content > .badge-marker").exists()).toBe(true)
        expect(wrapper.find(".node-title .badge-marker").exists()).toBe(false)
    })

    it("should render the status and actions as direct children of the main content", () => {
        const wrapper = mountBasicNode()

        expect(wrapper.find(".main-content > .status-marker").exists()).toBe(true)
        expect(wrapper.find(".main-content > .actions-marker").exists()).toBe(true)

        expect(wrapper.find(".node-content .status-marker").exists()).toBe(false)
        expect(wrapper.find(".node-content .actions-marker").exists()).toBe(false)
    })
})
