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
})
