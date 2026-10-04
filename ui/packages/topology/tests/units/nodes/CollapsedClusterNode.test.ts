import {describe, expect, it, vi} from "vitest"

vi.mock("@vue-flow/core", () => ({
    Handle: {
        template: "<div class='mock-handle'></div>",
    },
    Position: {
        Top: "top",
        Bottom: "bottom",
        Left: "left",
        Right: "right",
    },
}))

import CollapsedClusterNode from "../../../src/nodes/CollapsedClusterNode.vue"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

function mountCollapsedClusterNode({
    expandable = false,
    isFlowableLane = false,
}: {
    expandable?: boolean
    isFlowableLane?: boolean
} = {}) {
    return i18nMount(CollapsedClusterNode, {
        props: {
            id: "cluster_root.parallel_task",
            data: {
                color: "flowable-task",
                expandable,
                isFlowableLane,
                isReadOnly: false,
                childTaskIds: [],
                taskNode: null,
            },
        },
        global: {
            stubs: {
                LaneHeader: {
                    template: "<div class='lane-header'><slot name='lead' /></div>",
                },
                KsTooltip: {
                    template: "<span><slot /></span>",
                },
                UnfoldMoreHorizontal: {
                    template: "<span class='button-icon'></span>",
                },
            },
        },
    })
}

describe("CollapsedClusterNode", () => {
    it("should render a collapsed cluster", () => {
        const wrapper = mountCollapsedClusterNode()

        expect(wrapper.find(".collapsed-cluster-node").exists()).toBe(true)
        expect(wrapper.find(".cluster-badge").exists()).toBe(true)
        expect(wrapper.find(".cluster-badge").text()).toBe("parallel_task")
    })

    it("should show the expand button when expandable", () => {
        const wrapper = mountCollapsedClusterNode({
            expandable: true,
        })

        expect(wrapper.find(".circle-button").exists()).toBe(true)
    })

    it("should hide the expand button when not expandable", () => {
        const wrapper = mountCollapsedClusterNode({
            expandable: false,
        })

        expect(wrapper.find(".circle-button").exists()).toBe(false)
    })

    it("should emit expand with the node id", async () => {
        const wrapper = mountCollapsedClusterNode({
            expandable: true,
        })

        await wrapper.find(".circle-button").trigger("click")

        expect(wrapper.emitted("expand")).toHaveLength(1)
        expect(wrapper.emitted("expand")![0][0]).toEqual({
            id: "cluster_root.parallel_task",
        })
    })
})