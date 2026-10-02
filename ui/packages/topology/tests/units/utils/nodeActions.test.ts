
import {describe, expect, it, vi} from "vitest"
import type {ComposerTranslation} from "vue-i18n"
import {SECTIONS} from "@kestra-io/design-system"
import {
    buildNodeActions,
    type NodeActionsContext,
    type NodeActionCallbacks,
} from "../../../src/utils/nodeActions"
import type {CustomActionConfig, ShowDetailsConfig} from "../../../src/utils/constants"

const t = ((key: string) => key) as unknown as ComposerTranslation

function createCallbacks() {
    return {
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
}

function createContext(
    overrides: Partial<NodeActionsContext> = {},
): NodeActionsContext {
    return {
        taskId: "task-1",
        task: {id: "task-1"},
        isReadOnly: false,
        isFlowable: true,
        expandable: false,
        taskRuns: [],
        taskRunsWithDynamicChildren: [],
        replayEnabled: false,
        ...overrides,
    }
}

describe("buildNodeActions", () => {

    // TEST 1: Description action
    it("adds the description action only when a description exists", () => {
        const callbacks = createCallbacks()

        const withDescription = buildNodeActions(
            createContext({
                task: {id: "task-1", description: "My task"},
            }),
            t,
            callbacks,
            {id: "task-1", type: "task"},
        )

        const withoutDescription = buildNodeActions(
            createContext(),
            t,
            callbacks,
            {id: "task-1", type: "task"},
        )

        expect(withDescription.map(action => action.key))
            .toContain("description")

        expect(withoutDescription.map(action => action.key))
            .not.toContain("description")

        const action = withDescription.find(
            item => item.key === "description",
        )!

        action.onClick()

        expect(callbacks.onShowDescription).toHaveBeenCalledWith({
            id: "task-1",
            description: "My task",
        })
    })

    // TEST 2: Condition action 
    it("adds the condition action only when runIf exists", () => {
        const callbacks = createCallbacks()

        const task = {
            id: "task-1",
            runIf: "true",
        }

        const withCondition = buildNodeActions(
            createContext({task}),
            t,
            callbacks,
            {id: "task-1", type: "task"},
        )

        const withoutCondition = buildNodeActions(
            createContext(),
            t,
            callbacks,
            {id: "task-1", type: "task"},
        )

        expect(withCondition.map(action => action.key))
            .toContain("condition")

        expect(withoutCondition.map(action => action.key))
            .not.toContain("condition")

        withCondition.find(
            action => action.key === "condition",
        )!.onClick()

        expect(callbacks.onShowCondition).toHaveBeenCalledWith({
            id: "task-1",
            task,
            section: SECTIONS.TASKS,
        })
    })

    // TEST 3: Logs action
    it("adds logs and outputs when an execution exists", () => {
    const callbacks = createCallbacks()

    const execution = { id: "execution-1" }
    const taskRuns = [{ id: "run-1" }]
    const taskRunsWithDynamicChildren = [{ id: "dynamic-run-1" }]

    const actions = buildNodeActions(
        createContext({
        taskExecution: execution,
        taskRuns,
        taskRunsWithDynamicChildren,
        }),
        t,
        callbacks,
        { id: "task-1", type: "task" },
    )

    const actionKeys = actions.map((action) => action.key)

    expect(actionKeys).toContain("logs")
    expect(actionKeys).toContain("outputs")

    actions.find((action) => action.key === "logs")!.onClick()
    expect(callbacks.onShowLogs).toHaveBeenCalledWith({
        id: "task-1",
        execution,
        taskRuns: taskRunsWithDynamicChildren,
    })

    actions.find((action) => action.key === "outputs")!.onClick()
    expect(callbacks.onShowOutputs).toHaveBeenCalledWith({
        id: "task-1",
        execution,
        taskRuns,
    })
    })

    //TEST 4: execution absent  
    it("does not add logs and outputs when execution is absent", () => {
    const callbacks = createCallbacks()

    const actions = buildNodeActions(
        createContext(),
        t,
        callbacks,
        { id: "task-1", type: "task" },
    )

    const actionKeys = actions.map((action) => action.key)

    expect(actionKeys).not.toContain("logs")
    expect(actionKeys).not.toContain("outputs")
    })

    // TEST 5: Open Action
    it("adds the open action only when a link exists", () => {
    const callbacks = createCallbacks()
    const link = { id: "link-1" }

    const withLink = buildNodeActions(
        createContext({ link }),
        t,
        callbacks,
        { id: "task-1", type: "task" },
    )

    const withoutLink = buildNodeActions(
        createContext(),
        t,
        callbacks,
        { id: "task-1", type: "task" },
    )

    expect(withLink.map((action) => action.key)).toContain("open")
    expect(withoutLink.map((action) => action.key)).not.toContain("open")

    withLink.find((action) => action.key === "open")!.onClick()

    expect(callbacks.onOpenLink).toHaveBeenCalledWith({ link })
    })

    //TEST 6: Expand Action
    it("adds the expand action and passes expandData to the callback", () => {
    const callbacks = createCallbacks()
    const expandData = {id: "task-1", type: "task"}

    const actions = buildNodeActions(
        createContext({expandable: true}),
        t,
        callbacks,
        expandData,
    )

    expect(actions.map((action) => action.key)).toContain("expand")

    actions.find((action) => action.key === "expand")!.onClick()

    expect(callbacks.onExpand).toHaveBeenCalledWith(expandData)
    })

    //TEST 7: Add error action    
    it("adds the add-error action only when all conditions are satisfied", () => {
    const callbacks = createCallbacks()

    const actions = buildNodeActions(
        createContext({
        isReadOnly: false,
        isFlowable: true,
        task: {id: "task-1"},
        }),
        t,
        callbacks,
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).toContain("add-error")

    actions.find((action) => action.key === "add-error")!.onClick()

    expect(callbacks.onAddError).toHaveBeenCalledWith({
        task: {id: "task-1"},
    })
    })
    //TEST 8
    it("does not add add-error when an execution exists", () => {
    const actions = buildNodeActions(
        createContext({
        taskExecution: {id: "execution-1"},
        }),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).not.toContain("add-error")
    })
    //TEST 9
    it("does not add add-error in read-only mode", () => {
    const actions = buildNodeActions(
        createContext({isReadOnly: true}),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).not.toContain("add-error")
    })
    //TEST 10
    it("does not add add-error when the task is not flowable", () => {
    const actions = buildNodeActions(
        createContext({isFlowable: false}),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).not.toContain("add-error")
    })
    //TEST 11
    it("does not add add-error when the task has errors", () => {
    const actions = buildNodeActions(
        createContext({
        task: {id: "task-1", errors: ["Something went wrong"]},
        }),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).not.toContain("add-error")
    })

    //TEST 12:Test read-only mode  
    it("does not add duplicate and delete actions in read-only mode", () => {
    const actions = buildNodeActions(
        createContext({isReadOnly: true}),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    const actionKeys = actions.map((action) => action.key)

    expect(actionKeys).not.toContain("duplicate")
    expect(actionKeys).not.toContain("delete")
    })

    //TEST 13: Test writable mode and callback payloads
    it("adds duplicate and delete actions in writable mode", () => {
    const callbacks = createCallbacks()

    const actions = buildNodeActions(
        createContext({isReadOnly: false}),
        t,
        callbacks,
        {id: "task-1", type: "task"},
    )

    const actionKeys = actions.map((action) => action.key)

    expect(actionKeys).toContain("duplicate")
    expect(actionKeys).toContain("delete")

    actions.find((action) => action.key === "duplicate")!.onClick()

    expect(callbacks.onDuplicate).toHaveBeenCalledWith({
        id: "task-1",
    })

    actions.find((action) => action.key === "delete")!.onClick()

    expect(callbacks.onDelete).toHaveBeenCalledWith({
        id: "task-1",
        section: SECTIONS.TASKS,
    })
    })

    // TEST 14: Custom Action Callback
    it("routes showCustomAction to the custom action callback", () => {
    const callbacks = createCallbacks()

    const config = {
        taskProp: "task",
        lang: "en",
    } as CustomActionConfig

    const actionConfig = {
        eventName: "showCustomAction" as const,
        config,
    }

    const task = {id: "task-1"}

    const actions = buildNodeActions(
        createContext({
        task,
        actionConfig,
        }),
        t,
        callbacks,
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).toContain("show-details")

    actions.find((action) => action.key === "show-details")!.onClick()

    expect(callbacks.onShowCustomAction).toHaveBeenCalledWith({
        task,
        customAction: config,
    })

    expect(callbacks.onShowDetails).not.toHaveBeenCalled()
    })

    // TEST 15: Show Details Callback
    it("routes showDetails to the details callback", () => {
    const callbacks = createCallbacks()

    const config = {
        label: "Task Details",
    } as ShowDetailsConfig

    const actionConfig = {
        eventName: "showDetails" as const,
        config,
    }

    const task = {id: "task-1"}

    const actions = buildNodeActions(
        createContext({
        task,
        actionConfig,
        }),
        t,
        callbacks,
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).toContain("show-details")

    actions.find((action) => action.key === "show-details")!.onClick()

    expect(callbacks.onShowDetails).toHaveBeenCalledWith({
        task,
        showDetails: config,
    })

    expect(callbacks.onShowCustomAction).not.toHaveBeenCalled()
    })

    //TEST 16: Replay Task Callback
    it("adds replay when enabled with an execution and task runs", () => {
    const callbacks = createCallbacks()

    const execution = {id: "execution-1"}
    const taskRuns = [{id: "run-1"}]

    const actions = buildNodeActions(
        createContext({
        replayEnabled: true,
        taskExecution: execution,
        taskRuns,
        }),
        t,
        callbacks,
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).toContain("replay")

    actions.find((action) => action.key === "replay")!.onClick()

    expect(callbacks.onReplayTask).toHaveBeenCalledWith({
        id: "task-1",
        execution,
        taskRuns,
    })
    })

    // TEST 17: Replay Task Callback Disabled
    it("does not add replay when replay is disabled", () => {
    const actions = buildNodeActions(
        createContext({
        replayEnabled: false,
        taskExecution: {id: "execution-1"},
        taskRuns: [{id: "run-1"}],
        }),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).not.toContain("replay")
    })

    //TEST 18: when execution is absent
    it("does not add replay when execution is absent", () => {
    const actions = buildNodeActions(
        createContext({
        replayEnabled: true,
        taskRuns: [{id: "run-1"}],
        }),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).not.toContain("replay")
    })

    // TEST 19: when task runs are empty
    it("does not add replay when task runs are empty", () => {
    const actions = buildNodeActions(
        createContext({
        replayEnabled: true,
        taskExecution: {id: "execution-1"},
        taskRuns: [],
        }),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).not.toContain("replay")
    })

    // TEST 20: the configured details label is preferred
    it("uses the configured label for the details action", () => {
    const callbacks = createCallbacks()

    const task = {id: "task-1"}
    const config = {
        label: "My Task Details",
    } as ShowDetailsConfig

    const actionConfig = {
        eventName: "showDetails" as const,
        config,
    }

    const actions = buildNodeActions(
        createContext({task, actionConfig}),
        t,
        callbacks,
        {id: "task-1", type: "task"},
    )

    const detailsAction = actions.find(
        (action) => action.key === "show-details",
    )

    expect(detailsAction).toBeDefined()
    expect(detailsAction?.label).toBe("My Task Details")
    })

    //test 21: the stable action order
    it("keeps actions in a stable order", () => {
    const task = {
        id: "task-1",
        description: "A task description",
        runIf: "true",
    }

    const actions = buildNodeActions(
        createContext({
        task,
        taskExecution: {id: "execution-1"},
        taskRuns: [{id: "run-1"}],
        taskRunsWithDynamicChildren: [{id: "run-1"}],
        link: {
            namespace: "company",
            id: "flow-1",
            executionId: "execution-1",
        },
        expandable: true,
        replayEnabled: true,
        actionConfig: {
            eventName: "showDetails" as const,
            config: {label: "Task Details"} as ShowDetailsConfig,
        },
        }),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    expect(actions.map((action) => action.key)).toEqual([
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

    // TEST 22: the translated fallback is used when the details label is missing
    it("uses the translated fallback when the details label is missing", () => {
    const callbacks = createCallbacks()
    const task = {id: "task-1"}

    const actionConfig = {
        eventName: "showDetails" as const,
        config: {} as ShowDetailsConfig,
    }

    const actions = buildNodeActions(
        createContext({task, actionConfig}),
        t,
        callbacks,
        {id: "task-1", type: "task"},
    )

    const detailsAction = actions.find(
        (action) => action.key === "show-details",
    )

    expect(detailsAction).toBeDefined()
    expect(detailsAction?.label).toBe("show details")
    })

    // TEST 23: the delete action is marked as dangerous
    it("marks the delete action as dangerous", () => {
    const actions = buildNodeActions(
        createContext(),
        t,
        createCallbacks(),
        {id: "task-1", type: "task"},
    )

    const deleteAction = actions.find(
        (action) => action.key === "delete",
    )

    expect(deleteAction).toBeDefined()
    expect(deleteAction?.danger).toBe(true)
    })
})