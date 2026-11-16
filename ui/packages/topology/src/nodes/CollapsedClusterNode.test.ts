import {describe, expect, it} from "vitest"
import {computed} from "vue"
import {Position} from "@vue-flow/core"
import CollapsedClusterNode from "./CollapsedClusterNode.vue"
import NodeMenu from "./NodeMenu.vue"
import {CLUSTER_TAG_STATUS, EVENTS} from "../utils/constants"
import {EXECUTION_INJECTION_KEY, SUBFLOWS_EXECUTIONS_INJECTION_KEY} from "../injectionKeys"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const TASK_NODE = {
    uid: "root.per_region",
    task: {id: "per_region", type: "io.kestra.plugin.core.flow.Loop"},
}

function mountCollapsedLane(slots?: Record<string, string>, withExecution = true) {
    return i18nMount(CollapsedClusterNode, {
        slots,
        props: {
            id: "root.per_region",
            data: {
                color: "flowable-task",
                isFlowableLane: true,
                isReadOnly: true,
                executionId: "execution-id",
                taskNode: TASK_NODE,
            },
        },
        global: {
            stubs: {NodeMenu: true, Handle: true},
            provide: {
                [EXECUTION_INJECTION_KEY as symbol]: computed(() => withExecution ? {
                    id: "execution-id",
                    taskRunList: [{taskId: "per_region", state: {current: "FAILED"}}],
                } : undefined),
                [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
            },
        },
    })
}

function mountCollapsedClusterNode({
    id = "root.parallel.my-cluster",
    color = "triggers",
    expandable,
}: {
    id?: string;
    color?: string;
    expandable?: boolean;
} = {}) {
    return i18nMount(CollapsedClusterNode, {
        props: {
            id,
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
            data: {color, expandable},
        },
        global: {
            stubs: {
                Handle: {
                    props: ["type", "position"],
                    template:
                        "<span class='handle-stub' :data-type='type' :data-position='position' />",
                },
                LaneHeader: true,
                KsTooltip: {template: "<span><slot /></span>"},
                UnfoldMoreHorizontal: true,
            },
        },
    })
}

describe("CollapsedClusterNode taskActions slot", () => {
    it("should render the taskActions slot in the collapsed card with the lane's task run", () => {
        const wrapper = mountCollapsedLane({
            taskActions: "<template #taskActions=\"{task, taskRun}\"><button data-test=\"custom-actions\">{{ task.id }}:{{ taskRun.state.current }}</button></template>",
        })

        expect(wrapper.find("[data-test='custom-actions']").text()).toBe("per_region:FAILED")
        expect(wrapper.findComponent(NodeMenu).exists()).toBe(false)
    })

    it("should keep the default menu when no taskActions slot is provided", () => {
        const wrapper = mountCollapsedLane()

        expect(wrapper.findComponent(NodeMenu).exists()).toBe(true)
    })
})

describe("CollapsedClusterNode badge", () => {
    it("should display the ID segment after the last dot", () => {
        const wrapper = mountCollapsedClusterNode({
            id: "root.parallel.my-cluster",
        })

        expect(wrapper.find(".cluster-badge").text()).toBe("my-cluster")
    })

    it.each(Object.entries(CLUSTER_TAG_STATUS))(
        "should map the known colour '%s' to status '%s'",
        (color, status) => {
            const wrapper = mountCollapsedClusterNode({color})
            const style = wrapper.find(".cluster-badge").attributes("style")

            expect(style).toContain(`var(--ks-status-${status})`)
        },
    )

    it("should use the info status for an unknown colour", () => {
        const wrapper = mountCollapsedClusterNode({color: "unknown-colour"})
        const style = wrapper.find(".cluster-badge").attributes("style")

        expect(style).toContain("var(--ks-status-info)")
    })
})

describe("CollapsedClusterNode expand control", () => {
    it("should show the expand button when expandable is true", () => {
        const wrapper = mountCollapsedClusterNode({expandable: true})

        expect(wrapper.find(".circle-button").exists()).toBe(true)
    })

    it("should not show the expand button when expandable is false", () => {
        const wrapper = mountCollapsedClusterNode({expandable: false})

        expect(wrapper.find(".circle-button").exists()).toBe(false)
    })

    it("should not show the expand button when expandable is absent", () => {
        const wrapper = mountCollapsedClusterNode()

        expect(wrapper.find(".circle-button").exists()).toBe(false)
    })

    it("should emit the expand event with the node ID when clicked", async () => {
        const id = "root.parallel.my-cluster"
        const wrapper = mountCollapsedClusterNode({id, expandable: true})

        await wrapper.find(".circle-button").trigger("click")

        expect(wrapper.emitted(EVENTS.EXPAND)).toEqual([[{id}]])
    })
})

describe("CollapsedClusterNode Vue Flow handles", () => {
    it("should render source and target handles", () => {
        const wrapper = mountCollapsedClusterNode()
        const handles = wrapper.findAll(".handle-stub")

        expect(handles).toHaveLength(2)
        expect(handles.map((handle) => handle.attributes("data-type"))).toEqual(
            ["source", "target"],
        )
    })
})
