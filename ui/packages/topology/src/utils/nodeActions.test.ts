import {describe, expect, it, vi} from "vitest"
import type {ComposerTranslation} from "vue-i18n"
import {SECTIONS} from "@kestra-io/design-system"
import {buildNodeActions, type NodeActionCallbacks, type NodeActionsContext} from "./nodeActions"

const t = ((key: string) => key) as unknown as ComposerTranslation
const expandData = {id: "subflow-node", type: "io.kestra.plugin.core.flow.Subflow"}
const execution = {id: "execution-1"}
const taskRuns = [{id: "run-1"}]
const config = {label: "Task Details", taskProp: "task", lang: "yaml"}
const task = {id: "task-1", description: "My task", runIf: "{{ inputs.enabled }}"}
const link = {namespace: "company.team", id: "subflow"}
const everything: Partial<NodeActionsContext> = {
    task,
    taskExecution: execution,
    taskRuns,
    taskRunsWithDynamicChildren: [...taskRuns, {id: "dynamic-run-1"}],
    link,
    expandable: true,
    replayEnabled: true,
    actionConfig: {eventName: "showDetails", config},
}

function setup(overrides: Partial<NodeActionsContext> = {}) {
    const callbacks = {
        onShowDescription: vi.fn(),
        onShowCondition: vi.fn(),
        onShowLogs: vi.fn(),
        onShowOutputs: vi.fn(),
        onOpenLink: vi.fn(),
        onExpand: vi.fn(),
        onAddError: vi.fn(),
        onShowCustomAction: vi.fn(),
        onShowDetails: vi.fn(),
        onDuplicate: vi.fn(),
        onDelete: vi.fn(),
        onReplayTask: vi.fn(),
    } satisfies NodeActionCallbacks
    const actions = buildNodeActions({
        taskId: "task-1",
        task: {id: "task-1"},
        isReadOnly: false,
        isFlowable: true,
        expandable: false,
        taskRuns: [],
        taskRunsWithDynamicChildren: [],
        replayEnabled: false,
        ...overrides,
    }, t, callbacks, expandData)
    return {
        callbacks,
        actions,
        keys: actions.map((action) => action.key),
        action: (key: string) => actions.find((action) => action.key === key)!,
    }
}

describe("buildNodeActions", () => {
    it("returns every applicable action in a stable order", () => {
        expect(setup(everything).keys).toEqual([
            "description",
            "condition",
            "logs",
            "outputs",
            "open",
            "expand",
            "show-details",
            "duplicate",
            "delete",
            "replay",
        ])
    })

    it("calls each action's callback with its payload", () => {
        const {callbacks, actions} = setup(everything)

        actions.forEach((action) => action.onClick())

        expect(callbacks.onShowDescription).toHaveBeenCalledWith({id: "task-1", description: "My task"})
        expect(callbacks.onShowCondition).toHaveBeenCalledWith({id: "task-1", task, section: SECTIONS.TASKS})
        expect(callbacks.onShowLogs).toHaveBeenCalledWith({id: "task-1", execution, taskRuns: everything.taskRunsWithDynamicChildren})
        expect(callbacks.onShowOutputs).toHaveBeenCalledWith({id: "task-1", execution, taskRuns})
        expect(callbacks.onOpenLink).toHaveBeenCalledWith({link})
        expect(callbacks.onExpand).toHaveBeenCalledWith(expandData)
        expect(callbacks.onShowDetails).toHaveBeenCalledWith({task, showDetails: config})
        expect(callbacks.onShowCustomAction).not.toHaveBeenCalled()
        expect(callbacks.onDuplicate).toHaveBeenCalledWith({id: "task-1"})
        expect(callbacks.onDelete).toHaveBeenCalledWith({id: "task-1", section: SECTIONS.TASKS})
        expect(callbacks.onReplayTask).toHaveBeenCalledWith({id: "task-1", execution, taskRuns})
    })

    it("offers only add-error, duplicate and delete for a bare writable task", () => {
        const {callbacks, keys, action} = setup()

        action("add-error").onClick()

        expect(keys).toEqual(["add-error", "duplicate", "delete"])
        expect(callbacks.onAddError).toHaveBeenCalledWith({task: {id: "task-1"}})
    })

    it("offers nothing on a bare read-only task", () => {
        expect(setup({isReadOnly: true}).keys).toEqual([])
    })

    it.each<[string, Partial<NodeActionsContext>]>([
        ["an execution exists", {taskExecution: execution}],
        ["the task is not flowable", {isFlowable: false}],
        ["the task already has error handlers", {task: {id: "task-1", errors: [{id: "handler"}]}}],
    ])("hides add-error when %s", (_, overrides) => {
        expect(setup(overrides).keys).not.toContain("add-error")
    })

    it.each<[string, Partial<NodeActionsContext>]>([
        ["replay is disabled", {...everything, replayEnabled: false}],
        ["there is no execution", {...everything, taskExecution: undefined}],
        ["there are no task runs", {...everything, taskRuns: []}],
    ])("hides replay when %s", (_, overrides) => {
        expect(setup(overrides).keys).not.toContain("replay")
    })

    it("marks delete as danger and divides duplicate, delete and replay from the rest", () => {
        const {actions} = setup(everything)

        expect(actions.filter((action) => action.danger).map((action) => action.key)).toEqual(["delete"])
        expect(actions.filter((action) => action.divided).map((action) => action.key)).toEqual(["duplicate", "delete", "replay"])
    })

    it("routes a showCustomAction click to onShowCustomAction", () => {
        const {callbacks, action} = setup({...everything, actionConfig: {eventName: "showCustomAction", config}})

        action("show-details").onClick()

        expect(callbacks.onShowCustomAction).toHaveBeenCalledWith({task, customAction: config})
        expect(callbacks.onShowDetails).not.toHaveBeenCalled()
    })

    it("labels the details action with config.label, falling back to the translation", () => {
        expect(setup(everything).action("show-details").label).toBe("Task Details")
        expect(setup({...everything, actionConfig: {eventName: "showDetails", config: {...config, label: ""}}}).action("show-details").label)
            .toBe("show details")
    })

    it("hides the details action when there is no task to show", () => {
        expect(setup({...everything, task: undefined}).keys).not.toContain("show-details")
    })
})
