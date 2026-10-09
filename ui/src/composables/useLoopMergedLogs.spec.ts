import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {effectScope, nextTick, ref} from "vue"
import {AUTO_REFRESH_MS, useLoopMergedLogs} from "./useLoopMergedLogs"

const collectFailedTargets = vi.hoisted(() => vi.fn())
const collectScopedTargets = vi.hoisted(() => vi.fn())
const searchMergedLogs = vi.hoisted(() => vi.fn())

vi.mock("../utils/loopLogScope", () => ({collectFailedTargets, collectScopedTargets, searchMergedLogs}))

const targets = {executionIds: ["root"], chains: {}, failedChains: [], failedShown: 0, truncated: false, scope: []}

function setup(running: boolean) {
    const options = {
        root: ref({id: "root", namespace: "company.team", flowId: "close_lite"}),
        scope: ref([]),
        levelParams: ref({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"}),
        running: ref(running),
    }
    const scope = effectScope()
    const logs = scope.run(() => useLoopMergedLogs(options))!
    return {options, logs, scope}
}

describe("useLoopMergedLogs", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        collectFailedTargets.mockResolvedValue(targets)
        collectScopedTargets.mockResolvedValue(targets)
        searchMergedLogs.mockResolvedValue({results: [{message: "boom"}], total: 1, nextCursor: undefined, cursorMode: false})
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.clearAllMocks()
    })

    it("shouldRefreshOnAnIntervalWhileTheExecutionIsRunning", async () => {
        const {scope} = setup(true)
        await vi.advanceTimersByTimeAsync(0)
        expect(searchMergedLogs).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(AUTO_REFRESH_MS)

        expect(searchMergedLogs).toHaveBeenCalledTimes(2)
        scope.stop()
    })

    it("shouldNotRefreshOnAnIntervalOnceTheExecutionIsFinished", async () => {
        const {scope} = setup(false)
        await vi.advanceTimersByTimeAsync(AUTO_REFRESH_MS * 3)

        expect(searchMergedLogs).toHaveBeenCalledTimes(1)
        scope.stop()
    })

    it("shouldReloadWhenTheScopeChangesAndUseTheScopedCollector", async () => {
        const {options, scope} = setup(false)
        await vi.advanceTimersByTimeAsync(0)

        options.scope.value = [{taskId: "per_customer", number: 13}] as never
        await nextTick()
        await vi.advanceTimersByTimeAsync(0)

        expect(collectScopedTargets).toHaveBeenCalledTimes(1)
        scope.stop()
    })

    it("shouldExposeAFailureAndKeepRetryable", async () => {
        collectFailedTargets.mockRejectedValueOnce(new Error("boom"))
        const {logs, scope} = setup(false)
        await vi.advanceTimersByTimeAsync(0)

        expect(logs.failure.value).toBe("unknown")
        expect(logs.loaded.value).toBe(true)

        await logs.refresh()

        expect(logs.failure.value).toBeUndefined()
        expect(logs.lines.value).toHaveLength(1)
        scope.stop()
    })
})
