import {describe, expect, it} from "vitest"
import {computed} from "vue"
import TaskNode from "../../../src/nodes/TaskNode.vue"
import NodeMenu from "../../../src/nodes/NodeMenu.vue"
import {computeLongestTaskRunDuration} from "../../../src/misc/durationBreakdown"
import {
    EXECUTION_INJECTION_KEY,
    SUBFLOWS_EXECUTIONS_INJECTION_KEY,
    LONGEST_TASK_RUN_DURATION_INJECTION_KEY,
} from "../../../src/injectionKeys"

import {i18nMount} from "../../../../../tests/unit/i18nMount"

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

function taskRunWithHistory(taskId: string, histories: {date: number; state: string}[]) {
    return {
        id: `${taskId}-run`,
        taskId,
        state: {
            current: histories[histories.length - 1].state,
            histories,
        },
    }
}

function mountTaskNode({execution, taskRuns = [], replayEnabled = false, task = TASK, isReadOnly = true, isFlowable = false}: {
    execution?: Record<string, unknown>,
    taskRuns?: Record<string, unknown>[],
    replayEnabled?: boolean,
    task?: typeof TASK & {errors?: unknown[]},
    isReadOnly?: boolean,
    isFlowable?: boolean,
}) {
    return i18nMount(TaskNode, {
        props: {
            id: "root.my-task",
            data: {
                node: {
                    uid: "root.my-task",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task,
                    taskRun: taskRuns[0],
                },
                executionId: execution ? EXECUTION_ID : undefined,
                isReadOnly,
                isFlowable,
            },
            playgroundEnabled: false,
            playgroundReadyToStart: false,
            replayEnabled,
        },
        global: {
            stubs: {
                Handle: true,
                NodeMenu: true,
                BasicNode: {
                    template: "<div><slot name='badge'/><slot name='subtitle'/><slot name='details'/><slot name='content'/><slot name='footer'/><slot name='title-status'/><slot name='title-actions'/></div>",
                },
            },
            provide: {
                [EXECUTION_INJECTION_KEY as symbol]: computed(() =>
                    execution ? {id: EXECUTION_ID, taskRunList: taskRuns, ...execution} : undefined,
                ),
                [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
                [LONGEST_TASK_RUN_DURATION_INJECTION_KEY as symbol]: computed(() => computeLongestTaskRunDuration(taskRuns)),
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

    it("should offer add-error for an editable flowable task without error handlers", () => {
        const wrapper = mountTaskNode({isReadOnly: false, isFlowable: true})

        expect(actionKeys(wrapper)).toContain("add-error")
    })

    it("should offer add-error when the errors list exists but is empty", () => {
        const wrapper = mountTaskNode({
            isReadOnly: false,
            isFlowable: true,
            task: {...TASK, errors: []},
        })

        expect(actionKeys(wrapper)).toContain("add-error")
    })

    it("should not offer add-error when the task already has error handlers", () => {
        const wrapper = mountTaskNode({
            isReadOnly: false,
            isFlowable: true,
            task: {...TASK, errors: [{id: "handler", type: "io.kestra.plugin.core.log.Log"}]},
        })

        expect(actionKeys(wrapper)).not.toContain("add-error")
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

    it("should replace NodeMenu when the taskActions slot is provided, and support filtering actions", () => {
        const wrapper = i18nMount(TaskNode, {
            props: {
                id: "root.my-task",
                data: {
                    node: {
                        uid: "root.my-task",
                        type: "io.kestra.core.models.hierarchies.GraphTask",
                        task: TASK,
                        taskRun: taskRun({result: "value"}),
                    },
                    executionId: EXECUTION_ID,
                    isReadOnly: true,
                },
                playgroundEnabled: false,
                playgroundReadyToStart: false,
                replayEnabled: true,
            },
            global: {
                stubs: {
                    Handle: true,
                    NodeMenu: true,
                    BasicNode: {
                        template: "<div><slot name='title-actions'/></div>",
                    },
                },
                provide: {
                    [EXECUTION_INJECTION_KEY as symbol]: computed(() => ({
                        id: EXECUTION_ID,
                        taskRunList: [taskRun({result: "value"})],
                        state: {current: "SUCCESS"},
                    })),
                    [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
                },
            },
            slots: {
                taskActions: `
                    <template #default="{actions}">
                        <div id="custom-menu">
                            <span v-for="action in actions.filter(a => !['outputs', 'replay', 'edit'].includes(a.key))" :key="action.key" class="filtered-action">
                                {{ action.key }}
                            </span>
                        </div>
                    </template>
                `,
            },
        })

        expect(wrapper.findComponent(NodeMenu).exists()).toBe(false)
        expect(wrapper.find("#custom-menu").exists()).toBe(true)

        const actionKeys = wrapper.findAll(".filtered-action").map((w) => w.text())
        expect(actionKeys).toContain("logs") // Not filtered out
        expect(actionKeys).not.toContain("outputs") // Filtered out
        expect(actionKeys).not.toContain("replay") // Filtered out
        expect(actionKeys).not.toContain("edit") // Filtered out
    })
})

describe("TaskNode anatomy", () => {
    it("should show the task's type alongside its id", () => {
        const wrapper = mountTaskNode({task: {...TASK, type: "io.kestra.plugin.core.log.Log"}})

        expect(wrapper.text()).toContain("core.log.Log")
    })

    it("should show no duration bar outside of an execution context", () => {
        const wrapper = mountTaskNode({})

        expect(wrapper.find(".compact-bar").exists()).toBe(false)
    })

    it("should show no duration bar for a task that never ran", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [taskRunWithHistory("my-task", [{date: 0, state: "SKIPPED"}])],
        })

        expect(wrapper.find(".compact-bar").exists()).toBe(false)
    })

    it("should fill its own duration bar when it is the execution's longest task run", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [
                taskRunWithHistory("my-task", [{date: 0, state: "RUNNING"}, {date: 2_000, state: "SUCCESS"}]),
            ],
        })

        const running = wrapper.find(".split-bar-running")
        expect(running.exists()).toBe(true)
        expect((running.element as HTMLElement).style.width).toBe("100%")
    })

    it("should scale its bar against the longest task run of the execution, not its own duration", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [
                taskRunWithHistory("my-task", [{date: 0, state: "RUNNING"}, {date: 1_000, state: "SUCCESS"}]),
                taskRunWithHistory("other-task", [{date: 0, state: "RUNNING"}, {date: 4_000, state: "SUCCESS"}]),
            ],
        })

        const running = wrapper.find(".split-bar-running")
        expect((running.element as HTMLElement).style.width).toBe("25%")
    })
})
