import {describe, expect, it} from "vitest"
import {computed, ref} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import TaskNode from "../../../src/nodes/TaskNode.vue"
import NodeMenu from "../../../src/nodes/NodeMenu.vue"
import {
    EXECUTION_INJECTION_KEY,
    SUBFLOWS_EXECUTIONS_INJECTION_KEY,
    SHOW_EXTRA_DETAILS_INJECTION_KEY,
} from "../../../src/injectionKeys"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {}},
    missingWarn: false,
    fallbackWarn: false,
})

const TASK = {
    id: "my-task",
    type: "io.kestra.plugin.core.log.Log",
    default: null,
}

const EXECUTION_ID = "execution-id"

function taskRun(outputs?: Record<string, unknown>) {
    return {
        id: "taskrun-id",
        taskId: "my-task",
        state: {
            current: "SUCCESS",
            histories: [],
        },
        outputs,
    }
}

function mountTaskNode({execution, taskRuns = [], replayEnabled = false}: {
    execution?: Record<string, unknown>,
    taskRuns?: Record<string, unknown>[],
    replayEnabled?: boolean,
}) {
    return mount(TaskNode, {
        props: {
            id: "root.my-task",
            data: {
                node: {
                    uid: "root.my-task",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task: TASK,
                    taskRun: taskRuns[0],
                },
                executionId: execution ? EXECUTION_ID : undefined,
                isReadOnly: true,
            },
            playgroundEnabled: false,
            playgroundReadyToStart: false,
            replayEnabled,
        },
        global: {
            plugins: [i18n],
            stubs: {
                Handle: true,
                NodeMenu: true,
                BasicNode: {
                    template: "<div><slot name='badge'/><slot name='details'/><slot name='content'/><slot name='title-status'/><slot name='title-actions'/></div>",
                },
            },
            provide: {
                [EXECUTION_INJECTION_KEY as symbol]: computed(() =>
                    execution ? {id: EXECUTION_ID, taskRunList: taskRuns, ...execution} : undefined,
                ),
                [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
                [SHOW_EXTRA_DETAILS_INJECTION_KEY as symbol]: ref(false),
            },
        },
    })
}

function actionKeys(wrapper: ReturnType<typeof mountTaskNode>) {
    return wrapper.findComponent(NodeMenu).props("actions").map((action: {key: string}) => action.key)
}

describe("TaskNode actions", () => {
    it("should not offer execution actions outside of an execution context", () => {
        const wrapper = mountTaskNode({})

        const keys = actionKeys(wrapper)
        expect(keys).not.toContain("logs")
        expect(keys).not.toContain("outputs")
        expect(keys).not.toContain("replay")
    })

    it("should offer logs, outputs and replay in an execution context", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [taskRun({result: "value"})],
            replayEnabled: true,
        })

        const keys = actionKeys(wrapper)
        expect(keys).toContain("logs")
        expect(keys).toContain("outputs")
        expect(keys).toContain("replay")
    })

    it("should offer outputs in an execution context even when the run has none (empty state lives in the drawer)", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [taskRun()],
            replayEnabled: true,
        })

        const keys = actionKeys(wrapper)
        expect(keys).toContain("logs")
        expect(keys).toContain("outputs")
    })

    it("should not offer replay when replay is not enabled", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [taskRun({result: "value"})],
            replayEnabled: false,
        })

        expect(actionKeys(wrapper)).not.toContain("replay")
    })

    it("should emit showOutputs with the execution payload on outputs click", () => {
        const runs = [taskRun({result: "value"})]
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: runs,
            replayEnabled: true,
        })

        const actions = wrapper.findComponent(NodeMenu).props("actions")
        actions.find((action: {key: string}) => action.key === "outputs").onClick()

        const emitted = wrapper.emitted("showOutputs")
        expect(emitted).toHaveLength(1)
        expect(emitted![0][0]).toMatchObject({id: "my-task"})
    })

    it("should emit replayTask with the task runs on replay click", () => {
        const runs = [taskRun({result: "value"})]
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: runs,
            replayEnabled: true,
        })

        const actions = wrapper.findComponent(NodeMenu).props("actions")
        actions.find((action: {key: string}) => action.key === "replay").onClick()

        const emitted = wrapper.emitted("replayTask")
        expect(emitted).toHaveLength(1)
        expect(emitted![0][0]).toMatchObject({id: "my-task", taskRuns: runs})
    })
})
