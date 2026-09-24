import {describe, expect, it} from "vitest"
import {computed} from "vue"
import ClusterNode from "../../../src/nodes/ClusterNode.vue"
import NodeMenu from "../../../src/nodes/NodeMenu.vue"
import {EXECUTION_INJECTION_KEY, SUBFLOWS_EXECUTIONS_INJECTION_KEY} from "../../../src/injectionKeys"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const TASK_NODE = {
    uid: "root.parallel_task",
    task: {id: "parallel_task", type: "io.kestra.plugin.core.flow.Parallel"},
}

function taskRun(taskId: string, state: string) {
    return {taskId, state: {current: state}}
}

function mountClusterNode({childTaskIds = [], taskRuns = [], isReadOnly = false, isFlowableLane = true}: {
    childTaskIds?: string[],
    taskRuns?: Record<string, unknown>[],
    isReadOnly?: boolean,
    isFlowableLane?: boolean,
}) {
    return i18nMount(ClusterNode, {
        props: {
            id: "cluster_root.parallel_task",
            data: {
                color: "flowable-task",
                collaspsible: true,
                isFlowableLane,
                isReadOnly,
                childTaskIds,
                executionId: taskRuns.length ? "execution-id" : undefined,
                taskNode: isFlowableLane ? TASK_NODE : null,
            },
        },
        global: {
            stubs: {NodeMenu: true},
            provide: {
                [EXECUTION_INJECTION_KEY as symbol]: computed(() => (taskRuns.length ? {id: "execution-id", taskRunList: taskRuns} : undefined)),
                [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
            },
        },
    })
}

describe("ClusterNode lane header aggregate state", () => {
    it("should show no aggregate state when none of the children have run yet", () => {
        const wrapper = mountClusterNode({childTaskIds: ["branch_a", "branch_b"], taskRuns: []})

        expect(wrapper.find(".lane-state").exists()).toBe(false)
    })

    it("should show the shared success state when every child succeeded", () => {
        const wrapper = mountClusterNode({
            childTaskIds: ["branch_a", "branch_b"],
            taskRuns: [taskRun("branch_a", "SUCCESS"), taskRun("branch_b", "SUCCESS")],
        })

        expect(wrapper.find(".lane-state").exists()).toBe(true)
        expect(wrapper.find(".lane-state-text").text()).toBe("SUCCESS")
    })

    it("should surface a partial failure over the children that still succeeded", () => {
        const wrapper = mountClusterNode({
            childTaskIds: ["branch_a", "branch_b", "branch_c"],
            taskRuns: [taskRun("branch_a", "SUCCESS"), taskRun("branch_b", "FAILED"), taskRun("branch_c", "SUCCESS")],
        })

        expect(wrapper.find(".lane-state-text").text()).toBe("FAILED")
    })

    it("should show the child count", () => {
        const wrapper = i18nMount(ClusterNode, {
            props: {
                id: "cluster_root.parallel_task",
                data: {
                    color: "flowable-task",
                    collaspsible: true,
                    isFlowableLane: true,
                    isReadOnly: false,
                    childTaskIds: ["branch_a", "branch_b", "branch_c"],
                    taskNode: TASK_NODE,
                },
            },
            global: {
                stubs: {NodeMenu: true},
                provide: {
                    [EXECUTION_INJECTION_KEY as symbol]: computed(() => undefined),
                    [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
                },
            },
            messages: {"topology-graph": {"child-count": "{count} task | {count} tasks"}},
        })

        expect(wrapper.find(".lane-count").text()).toContain("3")
    })
})

describe("ClusterNode actions", () => {
    function actionKeys(wrapper: ReturnType<typeof mountClusterNode>) {
        return wrapper.findComponent(NodeMenu).props("actions").map((action: {key: string}) => action.key)
    }

    it("should offer duplicate and delete for an editable flowable", () => {
        const wrapper = mountClusterNode({isReadOnly: false})

        const keys = actionKeys(wrapper)
        expect(keys).toContain("duplicate")
        expect(keys).toContain("delete")
    })

    it("should not offer duplicate or delete when read-only", () => {
        const wrapper = mountClusterNode({isReadOnly: true})

        const keys = actionKeys(wrapper)
        expect(keys).not.toContain("duplicate")
        expect(keys).not.toContain("delete")
    })

    it("should emit edit when the header is clicked", async () => {
        const wrapper = mountClusterNode({isReadOnly: false})

        await wrapper.find(".lane-header").trigger("click")

        const emitted = wrapper.emitted("edit")
        expect(emitted).toHaveLength(1)
        expect(emitted![0][0]).toMatchObject({task: TASK_NODE.task})
    })

    it("should not emit edit when read-only", async () => {
        const wrapper = mountClusterNode({isReadOnly: true})

        await wrapper.find(".lane-header").trigger("click")

        expect(wrapper.emitted("edit")).toBeUndefined()
    })
})

describe("ClusterNode non-flowable clusters (Triggers, subflow)", () => {
    it("should keep the plain badge for a cluster with no flowable lane header", () => {
        const wrapper = mountClusterNode({isFlowableLane: false})

        expect(wrapper.find(".lane-header").exists()).toBe(false)
        expect(wrapper.find(".cluster-badge").exists()).toBe(true)
    })
})
