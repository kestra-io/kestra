import {test, expect, describe, vi} from "vitest"
import type {ComposerTranslation} from "vue-i18n"
import {SECTIONS} from "@kestra-io/design-system"
import {buildNodeActions, type NodeActionsContext} from "../../../src/utils/nodeActions.ts"

const t = ((key: string) => key) as unknown as ComposerTranslation

const baseCtx = (): NodeActionsContext => ({
    taskId: "task1",
    isReadOnly: false,
    isFlowable: false,
    expandable: false,
    taskRuns: [],
    taskRunsWithDynamicChildren: [],
    replayEnabled: false,
})

const baseCallbacks = () => ({
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
})

const expandData = {id: "task1", type: "task"}

const build = (ctx: Partial<NodeActionsContext> = {}) => {
    const callbacks = baseCallbacks()
    const actions = buildNodeActions({...baseCtx(), ...ctx}, t, callbacks, expandData)
    return {actions, callbacks}
}

const keys = (actions: {key: string}[]) => actions.map((action) => action.key)

describe("buildNodeActions", () => {
    test("shows the description action only when the task has a description", () => {
        const withDescription = build({task: {id: "task1", description: "does things"}})
        expect(keys(withDescription.actions)).toContain("description")

        const withoutDescription = build({task: {id: "task1"}})
        expect(keys(withoutDescription.actions)).not.toContain("description")
    })

    test("shows the condition action only when the task has a runIf", () => {
        const withRunIf = build({task: {id: "task1", runIf: "{{ outputs.check == 1 }}"}})
        expect(keys(withRunIf.actions)).toContain("condition")

        const withoutRunIf = build({task: {id: "task1"}})
        expect(keys(withoutRunIf.actions)).not.toContain("condition")
    })

    test("an execution adds the logs and outputs actions, no execution adds neither", () => {
        const withExecution = build({
            task: {id: "task1"},
            taskExecution: {id: "exec1"},
            taskRuns: [{id: "run1"}],
            taskRunsWithDynamicChildren: [{id: "run1"}],
        })
        expect(keys(withExecution.actions)).toContain("logs")
        expect(keys(withExecution.actions)).toContain("outputs")

        const withoutExecution = build({task: {id: "task1"}})
        expect(keys(withoutExecution.actions)).not.toContain("logs")
        expect(keys(withoutExecution.actions)).not.toContain("outputs")
    })

    test("a link adds the open action", () => {
        const withLink = build({task: {id: "task1"}, link: {namespace: "ns", id: "flow"}})
        expect(keys(withLink.actions)).toContain("open")

        const withoutLink = build({task: {id: "task1"}})
        expect(keys(withoutLink.actions)).not.toContain("open")
    })

    test("an expandable node adds the expand action which receives the expandData", () => {
        const {actions, callbacks} = build({task: {id: "task1"}, expandable: true})
        expect(keys(actions)).toContain("expand")

        actions.find((action) => action.key === "expand")!.onClick()
        expect(callbacks.onExpand).toHaveBeenCalledExactlyOnceWith(expandData)

        const notExpandable = build({task: {id: "task1"}})
        expect(keys(notExpandable.actions)).not.toContain("expand")
    })

    test("the add-error action requires no execution, writable, flowable and no errors yet", () => {
        const base = {task: {id: "task1"}, isFlowable: true}
        expect(keys(build(base).actions)).toContain("add-error")

        expect(keys(build({...base, taskExecution: {id: "exec1"}}).actions)).not.toContain("add-error")
        expect(keys(build({...base, isReadOnly: true}).actions)).not.toContain("add-error")
        expect(keys(build({...base, isFlowable: false}).actions)).not.toContain("add-error")
        expect(keys(build({...base, task: {id: "task1", errors: [{id: "err"}]}}).actions)).not.toContain("add-error")
    })

    test("a read-only node offers neither duplicate nor delete, a writable one offers both", () => {
        const readOnly = build({task: {id: "task1"}, isReadOnly: true})
        expect(keys(readOnly.actions)).not.toContain("duplicate")
        expect(keys(readOnly.actions)).not.toContain("delete")

        const writable = build({task: {id: "task1"}})
        expect(keys(writable.actions)).toContain("duplicate")
        expect(keys(writable.actions)).toContain("delete")
    })

    test("the delete action is danger and duplicate, delete and replay are divided", () => {
        const {actions} = build({
            task: {id: "task1"},
            taskExecution: {id: "exec1"},
            taskRuns: [{id: "run1"}],
            replayEnabled: true,
        })

        const byKey = Object.fromEntries(actions.map((action) => [action.key, action]))
        expect(byKey["delete"].danger).toBe(true)
        expect(byKey["duplicate"].divided).toBe(true)
        expect(byKey["delete"].divided).toBe(true)
        expect(byKey["replay"].divided).toBe(true)
    })

    test("actionConfig routes the click by eventName", () => {
        const customAction = build({
            task: {id: "task1"},
            actionConfig: {config: {label: "Do it"}, eventName: "showCustomAction"},
        })
        customAction.actions.find((action) => action.key === "show-details")!.onClick()
        expect(customAction.callbacks.onShowCustomAction).toHaveBeenCalledExactlyOnceWith({
            task: {id: "task1"},
            customAction: {label: "Do it"},
        })
        expect(customAction.callbacks.onShowDetails).not.toHaveBeenCalled()

        const showDetails = build({
            task: {id: "task1"},
            actionConfig: {config: {label: "Details"}, eventName: "showDetails"},
        })
        showDetails.actions.find((action) => action.key === "show-details")!.onClick()
        expect(showDetails.callbacks.onShowDetails).toHaveBeenCalledExactlyOnceWith({
            task: {id: "task1"},
            showDetails: {label: "Details"},
        })
        expect(showDetails.callbacks.onShowCustomAction).not.toHaveBeenCalled()
    })

    test("the details action prefers config.label over the translated fallback", () => {
        const withLabel = build({
            task: {id: "task1"},
            actionConfig: {config: {label: "Custom label"}, eventName: "showDetails"},
        })
        expect(withLabel.actions.find((action) => action.key === "show-details")!.label).toBe("Custom label")

        const withoutLabel = build({
            task: {id: "task1"},
            actionConfig: {config: {}, eventName: "showDetails"},
        })
        expect(withoutLabel.actions.find((action) => action.key === "show-details")!.label).toBe("show details")
    })

    test("replay appears only when replayEnabled, an execution exists and a task run is present", () => {
        const base = {task: {id: "task1"}, taskExecution: {id: "exec1"}, taskRuns: [{id: "run1"}]}
        expect(keys(build({...base, replayEnabled: true}).actions)).toContain("replay")

        expect(keys(build({...base, replayEnabled: false}).actions)).not.toContain("replay")
        expect(keys(build({task: {id: "task1"}, taskRuns: [{id: "run1"}], replayEnabled: true}).actions)).not.toContain("replay")
        expect(keys(build({task: {id: "task1"}, taskExecution: {id: "exec1"}, taskRuns: [], replayEnabled: true}).actions)).not.toContain("replay")
    })

    test("every onClick invokes its callback with the documented payload", () => {
        const link = {namespace: "ns", id: "flow", executionId: "exec1"}
        const execution = {id: "exec1"}
        const taskRuns = [{id: "run1"}]
        const {actions, callbacks} = build({
            task: {id: "task1", description: "does things", runIf: "{{ 1 == 1 }}"},
            taskExecution: execution,
            taskRuns,
            taskRunsWithDynamicChildren: taskRuns,
            replayEnabled: true,
            link,
            expandable: true,
            isFlowable: true,
        })

        const byKey = Object.fromEntries(actions.map((action) => [action.key, action]))
        byKey["description"].onClick()
        expect(callbacks.onShowDescription).toHaveBeenCalledExactlyOnceWith({id: "task1", description: "does things"})

        byKey["condition"].onClick()
        expect(callbacks.onShowCondition).toHaveBeenCalledExactlyOnceWith({id: "task1", task: {id: "task1", description: "does things", runIf: "{{ 1 == 1 }}"}, section: SECTIONS.TASKS})

        byKey["logs"].onClick()
        expect(callbacks.onShowLogs).toHaveBeenCalledExactlyOnceWith({id: "task1", execution, taskRuns})

        byKey["outputs"].onClick()
        expect(callbacks.onShowOutputs).toHaveBeenCalledExactlyOnceWith({id: "task1", execution, taskRuns})

        byKey["open"].onClick()
        expect(callbacks.onOpenLink).toHaveBeenCalledExactlyOnceWith({link})

        byKey["expand"].onClick()
        expect(callbacks.onExpand).toHaveBeenCalledExactlyOnceWith(expandData)

        byKey["duplicate"].onClick()
        expect(callbacks.onDuplicate).toHaveBeenCalledExactlyOnceWith({id: "task1"})

        byKey["delete"].onClick()
        expect(callbacks.onDelete).toHaveBeenCalledExactlyOnceWith({id: "task1", section: SECTIONS.TASKS})

        byKey["replay"].onClick()
        expect(callbacks.onReplayTask).toHaveBeenCalledExactlyOnceWith({id: "task1", execution, taskRuns})
    })

    test("actions come back in a stable order", () => {
        const {actions} = build({
            task: {id: "task1", description: "does things", runIf: "{{ 1 == 1 }}"},
            taskExecution: {id: "exec1"},
            taskRuns: [{id: "run1"}],
            taskRunsWithDynamicChildren: [{id: "run1"}],
            replayEnabled: true,
            link: {namespace: "ns", id: "flow"},
            expandable: true,
            isFlowable: true,
        })

        expect(keys(actions)).toEqual([
            "description",
            "condition",
            "logs",
            "outputs",
            "open",
            "expand",
            "duplicate",
            "delete",
            "replay",
        ])
    })
})
