import {describe, expect, it} from "vitest"
import {computed} from "vue"
import CollapsedClusterNode from "./CollapsedClusterNode.vue"
import NodeMenu from "./NodeMenu.vue"
import {EXECUTION_INJECTION_KEY, SUBFLOWS_EXECUTIONS_INJECTION_KEY} from "../injectionKeys"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const TASK_NODE = {
    uid: "root.per_region",
    task: {id: "per_region", type: "io.kestra.plugin.core.flow.Loop"},
}

function mountCollapsedLane(slots?: Record<string, string>) {
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
                [EXECUTION_INJECTION_KEY as symbol]: computed(() => ({
                    id: "execution-id",
                    taskRunList: [{taskId: "per_region", state: {current: "FAILED"}}],
                })),
                [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
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
