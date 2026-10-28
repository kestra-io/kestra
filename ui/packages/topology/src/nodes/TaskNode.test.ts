import {describe, expect, it} from "vitest"
import {computed} from "vue"
import {NODE_SIZES} from "../utils/constants"
import TaskNode from "./TaskNode.vue"
import NodeMenu from "./NodeMenu.vue"
import {computeLongestTaskRunDuration} from "../misc/durationBreakdown"
import {
    EXECUTION_INJECTION_KEY,
    SUBFLOWS_EXECUTIONS_INJECTION_KEY,
    LONGEST_TASK_RUN_DURATION_INJECTION_KEY,
    VALIDATION_ISSUES_INJECTION_KEY,
    LOOP_LANES_INJECTION_KEY,
    LOD_INJECTION_KEY,
    ARTIFACT_COUNTS_INJECTION_KEY,
} from "../injectionKeys"

import type {LoopLaneData} from "../utils/loopOutcome"
import en from "../../../../src/translations/en.json"
import type {GraphTaskRun} from "../utils/vueFlowUtils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

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

function mountTaskNode({execution, taskRuns = [], replayEnabled = false, task = TASK, isReadOnly = true, isFlowable = false, realBasicNode = false, validationIssues = new Map(), loopLanes, uid = "root.my-task", artifactCounts = {}, lod = "default", nodeExecutionId, sourcePosition}: {
    sourcePosition?: string,
    loopLanes?: Record<string, LoopLaneData>,
    artifactCounts?: Record<string, number>,
    lod?: "pill" | "default" | "expanded",
    nodeExecutionId?: string,
    uid?: string,
    execution?: Record<string, unknown>,
    validationIssues?: Map<string, string[]>,
    taskRuns?: GraphTaskRun[],
    replayEnabled?: boolean,
    task?: typeof TASK & {errors?: unknown[], taskRunner?: {type: string}},
    isReadOnly?: boolean,
    isFlowable?: boolean,
    realBasicNode?: boolean,
}) {
    return i18nMount(TaskNode, {
        locales: en,
        props: {
            id: uid,
            data: {
                node: {
                    uid,
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task,
                    taskRun: taskRuns[0],
                },
                executionId: nodeExecutionId ?? (execution ? EXECUTION_ID : undefined),
                isReadOnly,
                isFlowable,
            },
            playgroundEnabled: false,
            playgroundReadyToStart: false,
            replayEnabled,
            ...(sourcePosition ? {sourcePosition} : {}),
        },
        global: {
            stubs: {
                Handle: true,
                NodeMenu: true,
                ...(realBasicNode
                    ? {KsTooltip: {template: "<span><slot /></span>"}}
                    : {
                        BasicNode: {
                            template: "<div><slot name='runner'/><slot name='icon-badge'/><slot name='subtitle'/><slot name='details'/><slot name='content'/><slot name='footer'/><slot name='title-status'/><slot name='status-row'/><slot name='title-actions'/></div>",
                        },
                    }),
            },
            provide: {
                [EXECUTION_INJECTION_KEY as symbol]: computed(() =>
                    execution ? {id: EXECUTION_ID, taskRunList: taskRuns, ...execution} : undefined,
                ),
                [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
                [LONGEST_TASK_RUN_DURATION_INJECTION_KEY as symbol]: computed(() => computeLongestTaskRunDuration(taskRuns)),
                [VALIDATION_ISSUES_INJECTION_KEY as symbol]: computed(() => validationIssues),
                [LOOP_LANES_INJECTION_KEY as symbol]: computed(() => loopLanes ?? {}),
                [LOD_INJECTION_KEY as symbol]: computed(() => lod),
                [ARTIFACT_COUNTS_INJECTION_KEY as symbol]: computed(() => artifactCounts),
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
        actions.find((action: {key: string}) => action.key === "outputs")?.onClick()

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
        actions.find((action: {key: string}) => action.key === "replay")?.onClick()

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

        const filteredKeys = wrapper.findAll(".filtered-action").map((w) => w.text())
        expect(filteredKeys).toContain("logs") // Not filtered out
        expect(filteredKeys).not.toContain("outputs") // Filtered out
        expect(filteredKeys).not.toContain("replay") // Filtered out
        expect(filteredKeys).not.toContain("edit") // Filtered out
    })
})

describe("TaskNode state", () => {
    it("should show the most severe state when the task ran more than once", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "WARNING"}},
            taskRuns: [
                {id: "run-1", taskId: "my-task", state: {current: "SUCCESS", histories: []}},
                {id: "run-2", taskId: "my-task", state: {current: "WARNING", histories: []}},
            ],
        })

        expect(wrapper.find("div").attributes("state")).toBe("WARNING")
    })
})

describe("TaskNode anatomy", () => {
    it("should show the task's type alongside its id", () => {
        const wrapper = mountTaskNode({task: {...TASK, type: "io.kestra.plugin.core.log.Log"}})

        expect(wrapper.text()).toContain("core.log.Log")
    })

    it("should show no duration bar outside of an execution context", () => {
        const wrapper = mountTaskNode({})

        expect(wrapper.find("[data-test=\"duration-compact-bar\"]").exists()).toBe(false)
    })

    it("should reserve no footer band outside of an execution context", () => {
        const wrapper = mountTaskNode({realBasicNode: true})

        expect(wrapper.find(".node-footer").exists()).toBe(false)
        expect(wrapper.find(".node-core").classes()).not.toContain("node-core--with-footer")
    })

    it("should show no duration bar for a task that never ran", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [taskRunWithHistory("my-task", [{date: 0, state: "SKIPPED"}])],
        })

        expect(wrapper.find("[data-test=\"duration-compact-bar\"]").exists()).toBe(false)
    })

    it("should fill its own duration bar when it is the execution's longest task run", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [
                taskRunWithHistory("my-task", [{date: 0, state: "RUNNING"}, {date: 2_000, state: "SUCCESS"}]),
            ],
        })

        const running = wrapper.find("[data-test=\"duration-segment-running\"]")
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

        const running = wrapper.find("[data-test=\"duration-segment-running\"]")
        expect((running.element as HTMLElement).style.width).toBe("25%")
    })

    it("should render the duration bar in the card footer", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [
                taskRunWithHistory("my-task", [{date: 0, state: "RUNNING"}, {date: 1_000, state: "SUCCESS"}]),
                taskRunWithHistory("other-task", [{date: 0, state: "RUNNING"}, {date: 4_000, state: "SUCCESS"}]),
            ],
            realBasicNode: true,
        })

        const bar = wrapper.find(".node-core > .node-footer [data-test=\"duration-compact-bar\"]")
        expect(bar.exists()).toBe(true)
        expect((bar.find("[data-test=\"duration-segment-running\"]").element as HTMLElement).style.width).toBe("25%")
    })
})

describe("TaskNode validation", () => {
    it("should outline a task that has validation errors", () => {
        const wrapper = mountTaskNode({validationIssues: new Map([["my-task", ["message: must not be null"]]])})

        expect(wrapper.find(".node-core--error").exists()).toBe(true)
    })

    it("should not outline a task without validation errors", () => {
        const wrapper = mountTaskNode({validationIssues: new Map([["other-task", ["message: must not be null"]]])})

        expect(wrapper.find(".node-core--error").exists()).toBe(false)
    })
})

describe("TaskNode inside a loop", () => {
    const lane = (overrides: Partial<LoopLaneData>): LoopLaneData => ({taskId: "per_region", status: "ready", parentScoped: true, ...overrides})
    const mountInLoop = (loop: LoopLaneData) => mountTaskNode({
        uid: "per_region.my-task",
        execution: {state: {current: "FAILED"}},
        loopLanes: {per_region: loop},
    })

    it("should summarize its runs and failures in the footer while the loop is unscoped", () => {
        const wrapper = mountInLoop(lane({taskRunStateCounts: {"my-task": {SUCCESS: 9, FAILED: 3}}}))

        expect(wrapper.find("[data-test='loop-task-runs']").text()).toBe("12 runs · 3 failed")
        expect(wrapper.find("[data-test=\"duration-compact-bar\"]").exists()).toBe(false)
    })

    it("should show no number when the counts are unavailable", () => {
        const wrapper = mountInLoop(lane({}))

        expect(wrapper.find("[data-test='loop-task-runs']").exists()).toBe(false)
    })

    it("should drop the summary once the loop is scoped to an iteration", () => {
        const wrapper = mountInLoop(lane({scopedNumber: 2, taskRunStateCounts: {"my-task": {SUCCESS: 9}}}))

        expect(wrapper.find("[data-test='loop-task-runs']").exists()).toBe(false)
    })
})

describe("TaskNode status chip row", () => {
    const mountWithState = (current: string, extra: Partial<GraphTaskRun> = {}) => mountTaskNode({
        execution: {state: {current}},
        taskRuns: [{id: "run-1", taskId: "my-task", state: {current, histories: []}, ...extra}],
        realBasicNode: true,
    })

    it.each(["SUCCESS", "FAILED", "KILLING", "RUNNING"])("should show a %s chip below the card, outside of it", (current) => {
        const wrapper = mountWithState(current)

        const chip = wrapper.find(".node-status-row [data-test='node-status-chip']")
        expect(chip.exists()).toBe(true)
        expect(chip.attributes("data-state")).toBe(current)
        expect(wrapper.find(".node-core [data-test='node-status-chip']").exists()).toBe(false)
    })

    it("should keep the chip row but show no chip for a task that never ran", () => {
        const wrapper = mountTaskNode({execution: {state: {current: "SUCCESS"}}, taskRuns: [], realBasicNode: true})

        expect(wrapper.find("[data-test='node-status-row']").exists()).toBe(true)
        expect(wrapper.find("[data-test='node-status-chip']").exists()).toBe(false)
    })

    it("should render no chip row outside of an execution context", () => {
        const wrapper = mountTaskNode({realBasicNode: true})

        expect(wrapper.find("[data-test='node-status-row']").exists()).toBe(false)
    })

    it("should render no chip row once zoomed out to the pill", () => {
        const wrapper = mountTaskNode({
            execution: {state: {current: "SUCCESS"}},
            taskRuns: [taskRun()],
            realBasicNode: true,
            lod: "pill",
        })

        expect(wrapper.find("[data-test='node-status-row']").exists()).toBe(false)
    })

    it("should keep the progress bar inside the card footer", () => {
        const wrapper = mountWithState("SUCCESS")

        expect(wrapper.find(".node-core > .node-footer").exists()).toBe(true)
    })
})

describe("TaskNode artifact badge", () => {
    const runs = [taskRun()]
    const mountWithArtifacts = (artifactCounts: Record<string, number>, nodeExecutionId?: string) => mountTaskNode({
        execution: {state: {current: "SUCCESS"}},
        taskRuns: runs,
        artifactCounts,
        nodeExecutionId,
        realBasicNode: true,
    })

    it("should not show a badge for a task without artifacts", () => {
        expect(mountWithArtifacts({}).find("[data-test='node-artifact-badge']").exists()).toBe(false)
        expect(mountWithArtifacts({"my-task": 0}).find("[data-test='node-artifact-badge']").exists()).toBe(false)
    })

    it("should show the singular count in the chip row", () => {
        const badge = mountWithArtifacts({"my-task": 1}).find(".node-status-row [data-test='node-artifact-badge']")

        expect(badge.text()).toBe("1 artifact")
    })

    it("should show the plural count in the chip row", () => {
        const badge = mountWithArtifacts({"my-task": 3}).find(".node-status-row [data-test='node-artifact-badge']")

        expect(badge.text()).toBe("3 artifacts")
    })

    it("should emit showArtifacts with the task and its actions when clicked", async () => {
        const wrapper = mountWithArtifacts({"my-task": 2})

        await wrapper.find("[data-test='node-artifact-badge']").trigger("click")

        const emitted = wrapper.emitted("showArtifacts")
        expect(emitted).toHaveLength(1)
        expect(emitted![0][0]).toMatchObject({id: "my-task", task: TASK, taskRuns: runs})
        expect((emitted![0][0] as {actions: unknown[]}).actions.length).toBeGreaterThan(0)
    })

    it("should not show a badge on a task of another execution that shares the id", () => {
        const wrapper = mountWithArtifacts({"my-task": 2}, "subflow-execution-id")

        expect(wrapper.find("[data-test='node-artifact-badge']").exists()).toBe(false)
    })
})

describe("TaskNode retry marker", () => {
    const mountWithAttempts = (attempts: unknown[]) => mountTaskNode({
        execution: {state: {current: "FAILED"}},
        taskRuns: [{id: "run-1", taskId: "my-task", state: {current: "FAILED", histories: []}, attempts}],
    })

    it("should flag a task run that took more than one attempt", () => {
        expect(mountWithAttempts([{}, {}]).find("[data-test='node-retry-marker']").exists()).toBe(true)
    })

    it("should not flag a task run that took a single attempt", () => {
        expect(mountWithAttempts([{}]).find("[data-test='node-retry-marker']").exists()).toBe(false)
    })
})

describe("TaskNode runner badge", () => {
    it("should show the runner above the task id rather than beside the type", () => {
        const wrapper = mountTaskNode({
            task: {...TASK, taskRunner: {type: "io.kestra.plugin.scripts.runner.docker.Docker"}},
            realBasicNode: true,
        })

        const content = wrapper.find(".node-content")
        expect(content.element.children[0].classList.contains("node-runner")).toBe(true)
        expect(content.find(".node-runner [data-test='node-runner-badge']").text()).toBe("SCRIPTS Docker")
        expect(content.find(".node-subtitle").text()).not.toContain("SCRIPTS")
    })
})

describe("TaskNode info action", () => {
    it("should hand the slot a task variant so the host can open the task popup", () => {
        const wrapper = i18nMount(TaskNode, {
            props: {
                id: "root.my-task",
                data: {
                    node: {uid: "root.my-task", type: "io.kestra.core.models.hierarchies.GraphTask", task: TASK},
                    executionId: EXECUTION_ID,
                    isReadOnly: true,
                },
                playgroundEnabled: false,
                playgroundReadyToStart: false,
            },
            global: {
                stubs: {Handle: true, NodeMenu: true, BasicNode: {template: "<div><slot name='title-actions'/></div>"}},
                provide: {
                    [EXECUTION_INJECTION_KEY as symbol]: computed(() => ({id: EXECUTION_ID, taskRunList: [taskRun()], state: {current: "SUCCESS"}})),
                    [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
                },
            },
            slots: {taskActions: "<template #default=\"{variant}\"><i data-test=\"variant\">{{ variant }}</i></template>"},
        })

        expect(wrapper.find("[data-test='variant']").text()).toBe("task")
    })
})

describe("TaskNode handles in a vertical layout", () => {
    const sourceHandle = (wrapper: ReturnType<typeof mountTaskNode>) => wrapper.findAll("handle-stub").find((handle) => handle.attributes("type") === "source")!
    const bottomOf = (wrapper: ReturnType<typeof mountTaskNode>) => (sourceHandle(wrapper).element as HTMLElement).style.bottom

    it("should leave the outgoing edge on the card bottom, above the reserved chip row", () => {
        const wrapper = mountTaskNode({execution: {state: {current: "SUCCESS"}}, taskRuns: [taskRun()], sourcePosition: "bottom"})

        expect(bottomOf(wrapper)).toBe(`${NODE_SIZES.TASK_STATUS_ROW_HEIGHT}px`)
    })

    it("should keep the outgoing edge at the node bottom once zoomed out to the pill, where no row is rendered", () => {
        const wrapper = mountTaskNode({execution: {state: {current: "SUCCESS"}}, taskRuns: [taskRun()], sourcePosition: "bottom", lod: "pill"})

        expect(bottomOf(wrapper)).toBe("")
    })
})
