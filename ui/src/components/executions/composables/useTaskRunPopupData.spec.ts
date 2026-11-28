import {describe, it, expect, vi, beforeEach} from "vitest"
import {ref, nextTick, type Ref} from "vue"
import {flushPromises} from "@vue/test-utils"

const searchByExecution = vi.hoisted(() => vi.fn())
vi.mock("@kestra-io/kestra-sdk/metrics", () => ({searchByExecution}))

const searchLogs = vi.hoisted(() => vi.fn())
vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs}))

const loadTaskRunOutputs = vi.hoisted(() => vi.fn())
vi.mock("../../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs}))

import {useTaskRunPopupData} from "./useTaskRunPopupData"

function deferred<T>() {
    let resolve!: (value: T | PromiseLike<T>) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
    })
    return {promise, resolve, reject}
}

describe("useTaskRunPopupData", () => {
    let enabled: Ref<boolean>
    let executionId: Ref<string | undefined>
    let taskId: Ref<string>
    let taskRunId: Ref<string | undefined>
    let logTaskRunId: Ref<string>
    let logAttemptNumber: Ref<number>
    let logChildTaskRunIds: Ref<string[]>
    let taskRunState: Ref<string | undefined>

    beforeEach(() => {
        vi.clearAllMocks()
        enabled = ref(true)
        executionId = ref("e1")
        taskId = ref("t1")
        taskRunId = ref("tr1")
        logTaskRunId = ref("tr1")
        logAttemptNumber = ref(0)
        logChildTaskRunIds = ref([])
        taskRunState = ref("RUNNING")

        searchByExecution.mockResolvedValue({total: 5})
        searchLogs.mockResolvedValue({total: 10, results: []})
        loadTaskRunOutputs.mockResolvedValue({out: "val"})
    })

    function createSource() {
        return {
            enabled: () => enabled.value,
            executionId: () => executionId.value,
            taskId: () => taskId.value,
            taskRunId: () => taskRunId.value,
            logTaskRunId: () => logTaskRunId.value,
            logAttemptNumber: () => logAttemptNumber.value,
            logChildTaskRunIds: () => logChildTaskRunIds.value,
            taskRunState: () => taskRunState.value,
        }
    }

    it("the status starts idle and stays idle while the key is undefined", async () => {
        enabled.value = false
        const data = useTaskRunPopupData(createSource())

        expect(data.metricsCount.status.value).toBe("idle")
        expect(data.metricsCount.value.value).toBe(0)
        expect(data.logsCount.status.value).toBe("idle")
        expect(data.logsCount.value.value).toBe(0)
        expect(data.outputs.status.value).toBe("idle")
        expect(data.outputs.value.value).toEqual({})
        expect(data.outputsCount.value).toBe(0)

        await nextTick()
        expect(data.metricsCount.status.value).toBe("idle")
        expect(searchByExecution).not.toHaveBeenCalled()
        expect(searchLogs).not.toHaveBeenCalled()
        expect(loadTaskRunOutputs).not.toHaveBeenCalled()
    })

    it("a key change moves the status to loading and then to ready with the loaded value", async () => {
        enabled.value = false
        const metricsDeferred = deferred<{total: number}>()
        const logsDeferred = deferred<{total: number; results: unknown[]}>()
        const outputsDeferred = deferred<Record<string, unknown>>()

        searchByExecution.mockReturnValue(metricsDeferred.promise)
        searchLogs.mockReturnValue(logsDeferred.promise)
        loadTaskRunOutputs.mockReturnValue(outputsDeferred.promise)

        const data = useTaskRunPopupData(createSource())
        expect(data.metricsCount.status.value).toBe("idle")

        enabled.value = true
        await nextTick()

        expect(data.metricsCount.status.value).toBe("loading")
        expect(data.logsCount.status.value).toBe("loading")
        expect(data.outputs.status.value).toBe("loading")

        metricsDeferred.resolve({total: 7})
        logsDeferred.resolve({total: 12, results: []})
        outputsDeferred.resolve({key1: "a", key2: "b"})

        await flushPromises()

        expect(data.metricsCount.status.value).toBe("ready")
        expect(data.metricsCount.value.value).toBe(7)
        expect(data.logsCount.status.value).toBe("ready")
        expect(data.logsCount.value.value).toBe(12)
        expect(data.outputs.status.value).toBe("ready")
        expect(data.outputs.value.value).toEqual({key1: "a", key2: "b"})
        expect(data.outputsCount.value).toBe(2)
    })

    it("a rejected load sets the status to error and resets the value to the empty one", async () => {
        const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {})
        searchByExecution.mockRejectedValue(new Error("network failure"))
        searchLogs.mockRejectedValue(new Error("logs error"))
        loadTaskRunOutputs.mockRejectedValue(new Error("outputs error"))

        const data = useTaskRunPopupData(createSource())

        await flushPromises()

        expect(data.metricsCount.status.value).toBe("error")
        expect(data.metricsCount.value.value).toBe(0)
        expect(data.logsCount.status.value).toBe("error")
        expect(data.logsCount.value.value).toBe(0)
        expect(data.outputs.status.value).toBe("error")
        expect(data.outputs.value.value).toEqual({})
        expect(data.outputsCount.value).toBe(0)
        expect(errorSpy).toHaveBeenCalledTimes(3)

        errorSpy.mockRestore()
    })

    it("a rejected reload resets a previously loaded value to the empty one", async () => {
        const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {})
        const data = useTaskRunPopupData(createSource())
        await flushPromises()

        expect(data.metricsCount.value.value).toBe(5)
        expect(data.logsCount.value.value).toBe(10)
        expect(data.outputs.value.value).toEqual({out: "val"})

        searchByExecution.mockRejectedValue(new Error("network failure"))
        searchLogs.mockRejectedValue(new Error("logs error"))
        loadTaskRunOutputs.mockRejectedValue(new Error("outputs error"))
        data.reload()
        await flushPromises()

        expect(data.metricsCount.status.value).toBe("error")
        expect(data.metricsCount.value.value).toBe(0)
        expect(data.logsCount.status.value).toBe("error")
        expect(data.logsCount.value.value).toBe(0)
        expect(data.outputs.status.value).toBe("error")
        expect(data.outputs.value.value).toEqual({})
        expect(data.outputsCount.value).toBe(0)

        errorSpy.mockRestore()
    })

    it("a slow load that resolves after a newer one has started is discarded, leaving the newer result in place", async () => {
        const firstMetrics = deferred<{total: number}>()
        const secondMetrics = deferred<{total: number}>()

        searchByExecution
            .mockReturnValueOnce(firstMetrics.promise)
            .mockReturnValueOnce(secondMetrics.promise)

        const data = useTaskRunPopupData(createSource())
        await nextTick()
        expect(data.metricsCount.status.value).toBe("loading")

        taskRunId.value = "tr2"
        await nextTick()

        secondMetrics.resolve({total: 42})
        await nextTick()
        await nextTick()

        expect(data.metricsCount.status.value).toBe("ready")
        expect(data.metricsCount.value.value).toBe(42)

        firstMetrics.resolve({total: 10})
        await nextTick()
        await nextTick()

        expect(data.metricsCount.value.value).toBe(42)
        expect(data.metricsCount.status.value).toBe("ready")
    })

    it("the base key is undefined when the popup is disabled, or when the execution or task run id is missing", async () => {
        enabled.value = false
        const dataDisabled = useTaskRunPopupData(createSource())
        expect(dataDisabled.metricsCount.status.value).toBe("idle")

        enabled.value = true
        executionId.value = undefined
        const dataNoExec = useTaskRunPopupData(createSource())
        expect(dataNoExec.metricsCount.status.value).toBe("idle")

        executionId.value = "e1"
        taskRunId.value = undefined
        const dataNoRun = useTaskRunPopupData(createSource())
        expect(dataNoRun.metricsCount.status.value).toBe("idle")

        await nextTick()
        expect(searchByExecution).not.toHaveBeenCalled()
    })

    it("the key changes when the task run state changes, so a finished run reloads", async () => {
        useTaskRunPopupData(createSource())
        await flushPromises()
        expect(searchByExecution).toHaveBeenCalledTimes(1)

        taskRunState.value = "SUCCESS"
        await flushPromises()
        expect(searchByExecution).toHaveBeenCalledTimes(2)
    })

    it("bumping the reload counter forces a reload with the same ids", async () => {
        const data = useTaskRunPopupData(createSource())
        await flushPromises()
        expect(searchByExecution).toHaveBeenCalledTimes(1)
        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(1)

        data.reload()
        await flushPromises()
        expect(searchByExecution).toHaveBeenCalledTimes(2)
        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(2)
    })

    it("a slow load that rejects after a newer one has started is discarded without an error", async () => {
        const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {})
        const firstMetrics = deferred<{total: number}>()
        const secondMetrics = deferred<{total: number}>()
        searchByExecution
            .mockReturnValueOnce(firstMetrics.promise)
            .mockReturnValueOnce(secondMetrics.promise)

        const data = useTaskRunPopupData(createSource())
        taskRunId.value = "tr2"
        await nextTick()

        secondMetrics.resolve({total: 42})
        await flushPromises()
        firstMetrics.reject(new Error("stale failure"))
        await flushPromises()

        expect(data.metricsCount.status.value).toBe("ready")
        expect(data.metricsCount.value.value).toBe(42)
        expect(errorSpy).not.toHaveBeenCalled()

        errorSpy.mockRestore()
    })

    it("a load still in flight when the key becomes undefined is discarded and the status goes back to idle", async () => {
        const pendingMetrics = deferred<{total: number}>()
        searchByExecution.mockReturnValueOnce(pendingMetrics.promise)

        const data = useTaskRunPopupData(createSource())
        expect(data.metricsCount.status.value).toBe("loading")

        enabled.value = false
        await nextTick()
        expect(data.metricsCount.status.value).toBe("idle")

        pendingMetrics.resolve({total: 42})
        await flushPromises()

        expect(data.metricsCount.status.value).toBe("idle")
        expect(data.metricsCount.value.value).toBe(0)
    })

    it("the metrics count asks for a single metric of the task run and falls back to 0 without a total", async () => {
        searchByExecution.mockResolvedValue({})
        const data = useTaskRunPopupData(createSource())
        await flushPromises()

        expect(searchByExecution).toHaveBeenCalledWith({executionId: "e1", taskId: "t1", taskRunId: "tr1", page: 1, size: 1})
        expect(data.metricsCount.status.value).toBe("ready")
        expect(data.metricsCount.value.value).toBe(0)
    })

    it("the logs count requests the task run's attempt once and falls back to the page length without a total", async () => {
        logTaskRunId.value = "log-tr1"
        searchLogs.mockResolvedValue({results: [{}, {}, {}]})
        const data = useTaskRunPopupData(createSource())
        await flushPromises()

        expect(searchLogs).toHaveBeenCalledTimes(1)
        expect(searchLogs).toHaveBeenCalledWith({
            filters: [
                {field: "executionId", operation: "EQUALS", value: "e1"},
                {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "INFO"},
                {field: "taskRunId", operation: "EQUALS", value: "log-tr1"},
                {field: "attemptNumber", operation: "EQUALS", value: "0"},
            ],
            page: 1,
            size: 1,
        })
        expect(data.logsCount.value.value).toBe(3)
    })

    it("the logs count adds the logs of the child task runs to the task run's own", async () => {
        logChildTaskRunIds.value = ["c1", "c2"]
        searchLogs.mockImplementation(({filters}: {filters: {operation: string}[]}) =>
            Promise.resolve({total: filters.some((filter) => filter.operation === "IN") ? 4 : 10, results: []}),
        )
        const data = useTaskRunPopupData(createSource())
        await flushPromises()

        expect(searchLogs).toHaveBeenCalledTimes(2)
        expect(searchLogs).toHaveBeenCalledWith(expect.objectContaining({
            filters: [
                {field: "executionId", operation: "EQUALS", value: "e1"},
                {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "INFO"},
                {field: "taskRunId", operation: "IN", value: ["c1", "c2"]},
            ],
        }))
        expect(data.logsCount.value.value).toBe(14)
    })

    it("changing the log attempt reloads the logs count and nothing else", async () => {
        useTaskRunPopupData(createSource())
        await flushPromises()
        expect(searchLogs).toHaveBeenCalledTimes(1)

        logAttemptNumber.value = 1
        await flushPromises()

        expect(searchLogs).toHaveBeenCalledTimes(2)
        expect(searchLogs).toHaveBeenLastCalledWith(expect.objectContaining({
            filters: expect.arrayContaining([{field: "attemptNumber", operation: "EQUALS", value: "1"}]),
        }))
        expect(searchByExecution).toHaveBeenCalledTimes(1)
        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(1)
    })
})
