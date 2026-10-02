import {describe, expect, it} from "vitest"
import {Handle, Position} from "@vue-flow/core"
import CollapsedClusterNode from "../../../src/nodes/CollapsedClusterNode.vue"
import LaneHeader from "../../../src/nodes/LaneHeader.vue"
import {EVENTS} from "../../../src/utils/constants"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const TASK_NODE = {
    uid: "root.parallel_task",
    task: {id: "parallel_task", type: "io.kestra.plugin.core.flow.Parallel"},
}

function mountCollapsedClusterNode({
    id = "cluster_root.my_cluster",
    data = {},
    sourcePosition = Position.Right,
    targetPosition = Position.Left,
}: {
    id?: string
    data?: Partial<{
        color: string
        expandable?: boolean
        isFlowableLane?: boolean
        isReadOnly?: boolean
        executionId?: string
        childTaskIds?: string[]
        taskNode?: typeof TASK_NODE | null
    }>
    sourcePosition?: Position
    targetPosition?: Position
} = {}) {
    return i18nMount(CollapsedClusterNode, {
        props: {
            id,
            sourcePosition,
            targetPosition,
            data: {
                color: "flowable-task",
                ...data,
            },
        },
        global: {
            stubs: {
                Handle: true,
                NodeMenu: true,
                KsTooltip: {template: "<span><slot /></span>"},
            },
        },
        messages: {"topology-graph": {"child-count": "{count} task | {count} tasks"}},
    })
}

describe("CollapsedClusterNode handles", () => {
    it("should render source and target handles with matching positions", () => {
        const wrapper = mountCollapsedClusterNode({
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
        })
        const handles = wrapper.findAllComponents(Handle)

        expect(handles).toHaveLength(2)
        expect(handles[0].props("type")).toBe("source")
        expect(handles[0].props("position")).toBe(Position.Right)
        expect(handles[1].props("type")).toBe("target")
        expect(handles[1].props("position")).toBe(Position.Left)
    })
})

describe("CollapsedClusterNode badge and styling", () => {
    it("should show the id shortened to the segment after the last dot", () => {
        const wrapper = mountCollapsedClusterNode({id: "cluster_root.my_cluster"})
        expect(wrapper.find(".cluster-badge").text()).toBe("my_cluster")
    })

    it("should resolve the badge style from CLUSTER_TAG_STATUS for a known color", () => {
        const triggersWrapper = mountCollapsedClusterNode({data: {color: "triggers"}})
        expect(triggersWrapper.find(".cluster-badge").attributes("style")).toContain("var(--ks-status-success)")

        const subflowWrapper = mountCollapsedClusterNode({data: {color: "subflow"}})
        expect(subflowWrapper.find(".cluster-badge").attributes("style")).toContain("var(--ks-status-running)")

        const errorsWrapper = mountCollapsedClusterNode({data: {color: "errors"}})
        expect(errorsWrapper.find(".cluster-badge").attributes("style")).toContain("var(--ks-status-error)")
    })

    it("should fall back to info status for an unknown color rather than producing an undefined variable", () => {
        const wrapper = mountCollapsedClusterNode({data: {color: "unknown-color"}})
        const style = wrapper.find(".cluster-badge").attributes("style")

        expect(style).toContain("var(--ks-status-info)")
        expect(style).not.toContain("undefined")
    })
})

describe("CollapsedClusterNode expand behavior", () => {
    it("should render the expand button when data.expandable is true", () => {
        const wrapper = mountCollapsedClusterNode({data: {expandable: true, color: "triggers"}})
        const expandButton = wrapper.find(".circle-button")

        expect(expandButton.exists()).toBe(true)
        expect(expandButton.attributes("style")).toContain("var(--ks-topology-btn-triggers)")
    })

    it("should not render the expand button when expandable is false or absent", () => {
        const absentWrapper = mountCollapsedClusterNode({data: {expandable: undefined}})
        expect(absentWrapper.find(".circle-button").exists()).toBe(false)

        const falseWrapper = mountCollapsedClusterNode({data: {expandable: false}})
        expect(falseWrapper.find(".circle-button").exists()).toBe(false)
    })

    it("should emit the expand event with the node id when clicked", async () => {
        const wrapper = mountCollapsedClusterNode({
            id: "cluster_root.my_cluster",
            data: {expandable: true},
        })

        await wrapper.find(".circle-button").trigger("click")
        expect(wrapper.emitted(EVENTS.EXPAND)).toEqual([[{id: "cluster_root.my_cluster"}]])
    })
})

describe("CollapsedClusterNode flowable lane mode", () => {
    it("should render LaneHeader when isFlowableLane is true", () => {
        const wrapper = mountCollapsedClusterNode({
            data: {
                isFlowableLane: true,
                taskNode: TASK_NODE,
            },
        })

        expect(wrapper.findComponent(LaneHeader).exists()).toBe(true)
        expect(wrapper.find(".collapsed-lane").exists()).toBe(true)
    })

    it("should show the count of collapsed child tasks in flowable lane mode", () => {
        const wrapper = mountCollapsedClusterNode({
            data: {
                isFlowableLane: true,
                taskNode: TASK_NODE,
                childTaskIds: ["branch_a", "branch_b", "branch_c"],
            },
        })

        expect(wrapper.find(".lane-count").text()).toContain("3")
    })

    it("should emit expand event when the lane expand button is clicked", async () => {
        const wrapper = mountCollapsedClusterNode({
            id: "cluster_root.parallel_task",
            data: {
                isFlowableLane: true,
                expandable: true,
                taskNode: TASK_NODE,
                color: "flowable-task",
            },
        })

        const expandButton = wrapper.find(".circle-button.lane-expand")
        expect(expandButton.exists()).toBe(true)

        await expandButton.trigger("click")
        expect(wrapper.emitted(EVENTS.EXPAND)).toEqual([[{id: "cluster_root.parallel_task"}]])
    })
})

describe("CollapsedClusterNode edge cases", () => {
    it("should handle empty label without crashing", () => {
        const wrapper = mountCollapsedClusterNode({id: ""})
        expect(wrapper.find(".cluster-badge").text()).toBe("")
    })

    it("should not render child count when childTaskIds is empty", () => {
        const wrapper = mountCollapsedClusterNode({
            data: {
                isFlowableLane: true,
                taskNode: TASK_NODE,
                childTaskIds: [],
            },
        })

        expect(wrapper.find(".lane-count").exists()).toBe(false)
    })

    it("should handle special characters in id", () => {
        const wrapper = mountCollapsedClusterNode({id: "cluster.Cluster & <Special>"})
        expect(wrapper.find(".cluster-badge").text()).toBe("Cluster & <Special>")
    })
})
