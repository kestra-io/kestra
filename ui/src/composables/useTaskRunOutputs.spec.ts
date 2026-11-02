import {describe, test, expect, vi, beforeEach} from "vitest"
import {ref} from "vue"
import {flushPromises} from "@vue/test-utils"

const taskOutputsInformationMock = vi.fn()
const taskRunOutputsMock = vi.fn()
const executionOutputsMock = vi.fn()
const clientGetMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk/outputs", () => ({
    taskOutputsInformation: (...args: unknown[]) => taskOutputsInformationMock(...args),
    taskRunOutputs: (...args: unknown[]) => taskRunOutputsMock(...args),
    executionOutputs: (...args: unknown[]) => executionOutputsMock(...args),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: (...args: unknown[]) => clientGetMock(...args)}),
}))

vi.mock("override/utils/route", () => ({apiUrl: () => "/api/v1/main"}))

import {useHasTaskRunOutputs, loadTaskRunOutputs, loadExecutionOutputs, callTaskRunEndpoint} from "./useTaskRunOutputs"

function rejectionHandlerOf(mock: ReturnType<typeof vi.fn>) {
    let handler: ((e: unknown) => unknown) | undefined
    mock.mockReturnValueOnce({catch: (onRejected: (e: unknown) => unknown) => {
        handler = onRejected
        return new Promise(() => {})
    }})
    return () => handler!
}

describe("useTaskRunOutputs", () => {
    beforeEach(() => {
        taskOutputsInformationMock.mockReset()
        taskRunOutputsMock.mockReset()
        executionOutputsMock.mockReset()
        clientGetMock.mockReset()
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

    test("treats a viewer without access to the outputs as having none, without raising an error", async () => {
        const forbidden = {status: 403, __kestra_handled: false}
        taskOutputsInformationMock.mockRejectedValue(forbidden)

        const hasOutputs = useHasTaskRunOutputs(ref("exec-forbidden"), ref("tr-1"), ref("SUCCESS"))
        await flushPromises()

        expect(hasOutputs.value).toBe(false)
        expect(forbidden.__kestra_handled).toBe(true)
    })

    test("still surfaces a failure other than a missing permission or a missing execution", async () => {
        const handler = rejectionHandlerOf(taskOutputsInformationMock)

        useHasTaskRunOutputs(ref("exec-failing"), ref("tr-1"), ref("SUCCESS"))
        await flushPromises()

        expect(() => handler()({status: 500})).toThrow()
    })

    test("loadTaskRunOutputs returns the fetched outputs", async () => {
        taskRunOutputsMock.mockResolvedValue({body: {id: 1}})

        const outputs = await loadTaskRunOutputs("exec-1", "tr-1")

        expect(outputs).toEqual({body: {id: 1}})
        expect(taskRunOutputsMock).toHaveBeenCalledWith({executionId: "exec-1", taskRunId: "tr-1"})
    })

    test("loadTaskRunOutputs returns an empty object when there are none", async () => {
        taskRunOutputsMock.mockResolvedValue(null)

        const outputs = await loadTaskRunOutputs("exec-1", "tr-2")

        expect(outputs).toEqual({})
    })

    test("loadExecutionOutputs returns the fetched outputs", async () => {
        executionOutputsMock.mockResolvedValue({key: "value"})

        const outputs = await loadExecutionOutputs("exec-1")

        expect(outputs).toEqual({key: "value"})
        expect(executionOutputsMock).toHaveBeenCalledWith({executionId: "exec-1"})
    })

    test("loadExecutionOutputs returns an empty object when there are none", async () => {
        executionOutputsMock.mockResolvedValue(null)

        const outputs = await loadExecutionOutputs("exec-2")

        expect(outputs).toEqual({})
    })

    test("callTaskRunEndpoint calls the plugin endpoint of the task run with its query parameters", async () => {
        clientGetMock.mockResolvedValue({data: {compiledSql: "select 1"}})

        const body = await callTaskRunEndpoint("io.kestra.plugin.dbt.cli.DbtCLI", "compiled-sql", "exec-1", "tr-1", {uniqueId: "model.shop.orders"})

        expect(body).toEqual({compiledSql: "select 1"})
        expect(clientGetMock).toHaveBeenCalledWith(
            "/api/v1/main/plugins/io.kestra.plugin.dbt.cli.DbtCLI/endpoints/compiled-sql/exec-1/tr-1",
            {params: {uniqueId: "model.shop.orders"}},
        )
    })

    test("callTaskRunEndpoint encodes the execution and task run ids", async () => {
        clientGetMock.mockResolvedValue({data: {}})

        await callTaskRunEndpoint("io.kestra.plugin.dbt.cli.DbtCLI", "compiled-sql", "exec-1", "../../users")

        expect(clientGetMock).toHaveBeenCalledWith(
            "/api/v1/main/plugins/io.kestra.plugin.dbt.cli.DbtCLI/endpoints/compiled-sql/exec-1/..%2F..%2Fusers",
            {params: {}},
        )
    })

    test.each([
        ["..", "tr-1"],
        ["compiled-sql", ".."],
        ["compiled-sql", "."],
    ])("callTaskRunEndpoint rejects the dot segment in name %s / task run %s", async (name, taskRunId) => {
        await expect(callTaskRunEndpoint("io.kestra.plugin.dbt.cli.DbtCLI", name, "exec-1", taskRunId)).rejects.toThrow("Invalid plugin endpoint path segment.")

        expect(clientGetMock).not.toHaveBeenCalled()
    })
})
