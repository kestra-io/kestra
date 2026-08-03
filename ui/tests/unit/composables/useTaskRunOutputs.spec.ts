import {describe, test, expect, vi, beforeEach} from "vitest"
import {ref} from "vue"
import {flushPromises} from "@vue/test-utils"

const taskOutputsInformationMock = vi.fn()
const taskRunOutputsMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk/outputs", () => ({
    taskOutputsInformation: (...args: unknown[]) => taskOutputsInformationMock(...args),
    taskRunOutputs: (...args: unknown[]) => taskRunOutputsMock(...args),
}))

import {useHasTaskRunOutputs, loadTaskRunOutputs} from "../../../src/composables/useTaskRunOutputs"

describe("useTaskRunOutputs", () => {
    beforeEach(() => {
        taskOutputsInformationMock.mockReset()
        taskRunOutputsMock.mockReset()
    })

    test("resolves true for a task run present in taskOutputsInformation", async () => {
        taskOutputsInformationMock.mockResolvedValue([{taskRunId: "tr-1"}, {taskRunId: "tr-2"}])

        const hasOutputs = useHasTaskRunOutputs(ref("exec-1"), ref("tr-1"), ref("SUCCESS"))
        await flushPromises()

        expect(hasOutputs.value).toBe(true)
    })

    test("resolves false for a task run absent from taskOutputsInformation", async () => {
        taskOutputsInformationMock.mockResolvedValue([{taskRunId: "tr-1"}])

        const hasOutputs = useHasTaskRunOutputs(ref("exec-2"), ref("tr-unknown"), ref("SUCCESS"))
        await flushPromises()

        expect(hasOutputs.value).toBe(false)
    })

    test("shares a single taskOutputsInformation request across task runs of the same execution", async () => {
        taskOutputsInformationMock.mockResolvedValue([{taskRunId: "tr-a"}])

        const executionId = ref("exec-shared")
        useHasTaskRunOutputs(executionId, ref("tr-a"), ref("SUCCESS"))
        useHasTaskRunOutputs(executionId, ref("tr-b"), ref("SUCCESS"))
        await flushPromises()

        expect(taskOutputsInformationMock).toHaveBeenCalledTimes(1)
    })

    test("bypasses the cache and re-checks when the task run's own state changes", async () => {
        taskOutputsInformationMock
            .mockResolvedValueOnce([])
            .mockResolvedValueOnce([{taskRunId: "tr-running"}])

        const taskRunState = ref("RUNNING")
        const hasOutputs = useHasTaskRunOutputs(ref("exec-live"), ref("tr-running"), taskRunState)
        await flushPromises()
        expect(hasOutputs.value).toBe(false)

        taskRunState.value = "SUCCESS"
        await flushPromises()

        expect(hasOutputs.value).toBe(true)
        expect(taskOutputsInformationMock).toHaveBeenCalledTimes(2)
    })

    test("loadTaskRunOutputs returns the fetched outputs", async () => {
        taskRunOutputsMock.mockResolvedValue({body: {id: 1}})

        const outputs = await loadTaskRunOutputs("exec-1", "tr-1")

        expect(outputs).toEqual({body: {id: 1}})
        expect(taskRunOutputsMock).toHaveBeenCalledWith(
            {executionId: "exec-1", taskRunId: "tr-1"},
            expect.objectContaining({validateStatus: expect.any(Function)}),
        )
    })

    test("loadTaskRunOutputs returns an empty object when there are none", async () => {
        taskRunOutputsMock.mockResolvedValue(null)

        const outputs = await loadTaskRunOutputs("exec-1", "tr-2")

        expect(outputs).toEqual({})
    })
})
