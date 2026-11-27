import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"

import {useTourActions} from "./useTourActions"

const mocks = vi.hoisted(() => ({
    executionsStore: {
        loadExecution: vi.fn(),
        loadLogs: vi.fn(),
    },
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, name: ""}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => mocks.executionsStore}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => ({})}))
vi.mock("../../../stores/plugins", () => ({usePluginsStore: () => ({})}))
vi.mock("../../../stores/productTour", () => ({
    useProductTourStore: () => ({state: {tour: {taskRunner: "docker"}}, setTourState: vi.fn()}),
}))

const STILL_RUNNING_MS = 180_000

const signal = new AbortController().signal

const execution = (state: string) => ({id: "exec-1", state: {current: state}})

const dockerLog = {
    level: "ERROR",
    taskId: "summarize",
    message: "Docker execution failed because Docker socket is not accessible.",
}

describe("tour actions", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        mocks.executionsStore.loadExecution.mockReset()
        mocks.executionsStore.loadLogs.mockReset()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("tells the user once that the execution is still running, then keeps waiting", async () => {
        mocks.executionsStore.loadExecution.mockResolvedValue(execution("RUNNING"))
        const onStillRunning = vi.fn()
        let settled = false

        const waiting = useTourActions().waitForExecution("exec-1", {signal, onStillRunning})
        void waiting.then(() => (settled = true))

        await vi.advanceTimersByTimeAsync(STILL_RUNNING_MS - 2000)
        expect(onStillRunning).not.toHaveBeenCalled()

        await vi.advanceTimersByTimeAsync(4000)
        expect(onStillRunning).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(10 * 60_000)
        expect(onStillRunning).toHaveBeenCalledTimes(1)
        expect(settled).toBe(false)

        mocks.executionsStore.loadExecution.mockResolvedValue(execution("SUCCESS"))
        await vi.advanceTimersByTimeAsync(1000)

        await expect(waiting).resolves.toMatchObject({state: {current: "SUCCESS"}})
        expect(onStillRunning).toHaveBeenCalledTimes(1)
    })

    it("returns as soon as the execution is terminated, without any notice", async () => {
        mocks.executionsStore.loadExecution
            .mockResolvedValueOnce(execution("RUNNING"))
            .mockResolvedValue(execution("FAILED"))
        const onStillRunning = vi.fn()

        const waiting = useTourActions().waitForExecution("exec-1", {signal, onStillRunning})
        await vi.advanceTimersByTimeAsync(1000)

        await expect(waiting).resolves.toMatchObject({state: {current: "FAILED"}})
        expect(onStillRunning).not.toHaveBeenCalled()
    })

    it("stops waiting when the tour is closed", async () => {
        mocks.executionsStore.loadExecution.mockResolvedValue(execution("RUNNING"))
        const controller = new AbortController()

        const waiting = useTourActions().waitForExecution("exec-1", {signal: controller.signal})
        const outcome = expect(waiting).rejects.toMatchObject({name: "AbortError"})
        await vi.advanceTimersByTimeAsync(3000)
        controller.abort()
        await vi.advanceTimersByTimeAsync(1000)
        await outcome

        const polls = mocks.executionsStore.loadExecution.mock.calls.length
        await vi.advanceTimersByTimeAsync(10_000)
        expect(mocks.executionsStore.loadExecution).toHaveBeenCalledTimes(polls)
    })

    it("finds the Docker error even when its logs are indexed late", async () => {
        mocks.executionsStore.loadLogs
            .mockResolvedValueOnce([])
            .mockResolvedValue([dockerLog])

        const detection = useTourActions().dockerUnavailable("exec-1", signal)
        await vi.advanceTimersByTimeAsync(1000)

        await expect(detection).resolves.toBe(true)
        expect(mocks.executionsStore.loadLogs).toHaveBeenCalledTimes(2)
        expect(mocks.executionsStore.loadLogs).toHaveBeenCalledWith(expect.objectContaining({
            executionId: "exec-1",
            store: false,
        }))
    })

    it("gives up after a few attempts when the logs never mention Docker", async () => {
        mocks.executionsStore.loadLogs.mockResolvedValue([
            {level: "ERROR", taskId: "summarize", message: "ModuleNotFoundError"},
        ])

        const detection = useTourActions().dockerUnavailable("exec-1", signal)
        await vi.advanceTimersByTimeAsync(10_000)

        await expect(detection).resolves.toBe(false)
        expect(mocks.executionsStore.loadLogs).toHaveBeenCalledTimes(4)
    })

    it("stops looking for the Docker error once the tour is closed", async () => {
        mocks.executionsStore.loadLogs.mockResolvedValue([])
        const controller = new AbortController()

        const detection = useTourActions().dockerUnavailable("exec-1", controller.signal)
        await vi.advanceTimersByTimeAsync(1000)
        controller.abort()
        await vi.advanceTimersByTimeAsync(10_000)

        await expect(detection).resolves.toBe(false)
        expect(mocks.executionsStore.loadLogs).toHaveBeenCalledTimes(2)
    })
})
