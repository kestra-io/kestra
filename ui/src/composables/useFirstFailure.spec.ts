import {beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia} from "pinia"
import {effectScope, nextTick, ref} from "vue"
import {useFirstFailure} from "./useFirstFailure"
import {useExecutionsStore, type Execution} from "../stores/executions"
import {routedPinia} from "../../tests/unit/memoryRouter"

const failedExecution = (id: string, taskRunId = "tr-1"): Execution => ({
    id,
    namespace: "ns",
    flowId: "flow",
    state: {current: "FAILED"},
    taskRunList: [{id: taskRunId, taskId: "reconcile", state: {current: "FAILED"}}],
} as unknown as Execution)

const flush = async () => {
    await nextTick()
    await Promise.resolve()
    await Promise.resolve()
}

describe("useFirstFailure", () => {
    beforeEach(() => {
        setActivePinia(routedPinia())
    })

    it("asks for the error logs of the failed task run only", async () => {
        // Given
        const store = useExecutionsStore()
        const loadLogs = vi.spyOn(store, "loadLogs").mockResolvedValue([{taskRunId: "tr-1", message: "boom `x`"}] as never)

        // When
        const {message} = effectScope().run(() => useFirstFailure(() => failedExecution("e1")))!
        await flush()

        // Then
        expect(loadLogs).toHaveBeenCalledWith(expect.objectContaining({
            executionId: "e1",
            filters: expect.arrayContaining([
                {field: "taskRunId", operation: "EQUALS", value: "tr-1"},
                {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"},
            ]),
        }))
        expect(message.value).toBe("boom x")
    })

    it("shows a sub-execution link as its execution id instead of raw link markup", async () => {
        // Given
        const store = useExecutionsStore()
        vi.spyOn(store, "loadLogs").mockResolvedValue([{
            taskRunId: "tr-1",
            message: "Loop iteration 2 ended in FAILED in sub-execution [[link execution=\"6NlmY8\" flowId=\"monthly_close\" namespace=\"company.finance\"]]",
        }] as never)

        // When
        const {message} = effectScope().run(() => useFirstFailure(() => failedExecution("e1")))!
        await flush()

        // Then
        expect(message.value).toBe("Loop iteration 2 ended in FAILED in sub-execution 6NlmY8")
    })

    it("does not borrow an error log from another task run", async () => {
        // Given
        const store = useExecutionsStore()
        vi.spyOn(store, "loadLogs").mockResolvedValue([{taskRunId: "other", message: "unrelated"}] as never)

        // When
        const {message} = effectScope().run(() => useFirstFailure(() => failedExecution("e1")))!
        await flush()

        // Then
        expect(message.value).toBeUndefined()
    })

    it("drops a response that arrives after the execution changed", async () => {
        // Given
        const store = useExecutionsStore()
        let resolveFirst: (logs: unknown[]) => void = () => {}
        vi.spyOn(store, "loadLogs")
            .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }) as never)
            .mockResolvedValueOnce([{taskRunId: "tr-2", message: "second"}] as never)
        const current = ref(failedExecution("e1", "tr-1"))
        const {message} = effectScope().run(() => useFirstFailure(current))!
        await flush()

        // When
        current.value = failedExecution("e2", "tr-2")
        await flush()
        resolveFirst([{taskRunId: "tr-1", message: "first"}])
        await flush()

        // Then
        expect(message.value).toBe("second")
    })

    it("stays idle while disabled", async () => {
        // Given
        const store = useExecutionsStore()
        const loadLogs = vi.spyOn(store, "loadLogs")

        // When
        const {failedTaskRun} = effectScope().run(() => useFirstFailure(() => failedExecution("e1"), false))!
        await flush()

        // Then
        expect(failedTaskRun.value?.id).toBe("tr-1")
        expect(loadLogs).not.toHaveBeenCalled()
    })
})
