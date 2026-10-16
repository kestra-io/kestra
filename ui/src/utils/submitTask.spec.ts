import {beforeEach, describe, expect, it, vi} from "vitest"
import {executeTask} from "./submitTask"
import type {Execution} from "../stores/executions"
import type {Flow} from "../stores/flow"

const {triggerExecution, executionsStore} = vi.hoisted(() => {
    const triggerExecution = vi.fn()
    const executionsStore: {
        triggerExecution: typeof triggerExecution
        execution: unknown
    } = {
        triggerExecution,
        execution: undefined,
    }
    return {triggerExecution, executionsStore}
})

vi.mock("../stores/executions", () => ({
    useExecutionsStore: () => executionsStore,
}))

const routerPush = vi.fn()
const toastSuccess = vi.fn()
const t = vi.fn((key: string, params?: Record<string, unknown>) => params?.name ? `${key}: ${String(params.name)}` : key)

const submitor: Parameters<typeof executeTask>[0] = {
    $router: {push: routerPush} as unknown as Parameters<typeof executeTask>[0]["$router"],
    $route: {params: {}, query: {}} as unknown as Parameters<typeof executeTask>[0]["$route"],
    $toast: () => ({success: toastSuccess}),
    $t: t,
}

const baseFlow: Flow = {
    id: "test-flow",
    namespace: "test-namespace",
    source: "",
    inputs: [],
} as unknown as Flow

const baseOptions: Parameters<typeof executeTask>[3] = {
    namespace: "test-namespace",
    id: "test-flow",
}

const mockExecution: Execution = {
    id: "exec-123",
    namespace: "test-namespace",
    flowId: "test-flow",
} as unknown as Execution

describe("executeTask", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        executionsStore.execution = undefined
        triggerExecution.mockResolvedValue(mockExecution)
    })

    it("forwards the namespace and flow id to triggerExecution", async () => {
        await executeTask(submitor, baseFlow, {}, baseOptions)

        expect(triggerExecution).toHaveBeenCalledWith(expect.objectContaining({
            namespace: "test-namespace",
            id: "test-flow",
            kind: "NORMAL",
        }))
    })

    it("forwards inputs in the expected shape with normalization and omitted empties", async () => {
        const flowWithInputs: Flow = {
            ...baseFlow,
            inputs: [
                {id: "datetimeInput", type: "DATETIME"},
                {id: "dateInput", type: "DATE"},
                {id: "timeInput", type: "TIME"},
                {id: "stringInput", type: "STRING"},
                {id: "emptyInput", type: "STRING"},
                {id: "nullInput", type: "STRING"},
                {id: "undefinedInput", type: "STRING"},
            ],
        } as unknown as Flow

        const values: Record<string, unknown> = {
            datetimeInput: "2024-01-15T10:30:45.000Z",
            dateInput: "2024-01-15",
            timeInput: "2024-01-15T10:30:45",
            stringInput: "test-value",
            emptyInput: "",
            nullInput: null,
            undefinedInput: undefined,
        }

        await executeTask(submitor, flowWithInputs, values, baseOptions)

        expect(triggerExecution).toHaveBeenCalledWith(expect.objectContaining({
            formData: {
                datetimeInput: "2024-01-15T10:30:45.000Z",
                dateInput: "2024-01-15",
                timeInput: "10:30:45",
                stringInput: "test-value",
            },
        }))
    })

    it("produces undefined formData for an empty input set", async () => {
        const flowWithNoInputs: Flow = {
            ...baseFlow,
            inputs: [],
        } as unknown as Flow

        await executeTask(submitor, flowWithNoInputs, {}, baseOptions)

        expect(triggerExecution).toHaveBeenCalledWith({
            ...baseOptions,
            kind: "NORMAL",
            formData: undefined,
        })
    })

    it("resolves with the execution returned by the store and triggers a success toast", async () => {
        const result = await executeTask(submitor, baseFlow, {}, baseOptions)

        expect(result).toBe(mockExecution)
        expect(executionsStore.execution).toBe(mockExecution)
        expect(t).toHaveBeenCalledWith("triggered done", {name: mockExecution.id})
        expect(toastSuccess).toHaveBeenCalledWith("triggered done: exec-123")
    })

    it("propagates rejection and avoids side effects when triggerExecution fails", async () => {
        const error = new Error("Failed to trigger execution")
        triggerExecution.mockRejectedValue(error)

        await expect(executeTask(submitor, baseFlow, {}, baseOptions)).rejects.toThrow("Failed to trigger execution")
        expect(executionsStore.execution).toBeUndefined()
        expect(toastSuccess).not.toHaveBeenCalled()
    })
})
