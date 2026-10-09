import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {effectScope, nextTick, ref} from "vue"
import {AUTO_REFRESH_MS, useLoopMergedLogs} from "./useLoopMergedLogs"

const collectFailedTargets = vi.hoisted(() => vi.fn())
const collectScopedTargets = vi.hoisted(() => vi.fn())
const searchMergedLogs = vi.hoisted(() => vi.fn())

vi.mock("../utils/loopLogScope", async (importOriginal) => ({
    ...await importOriginal<typeof import("../utils/loopLogScope")>(),
    collectFailedTargets,
    collectScopedTargets,
    searchMergedLogs,
}))

const targets = {executionIds: ["root"], chains: {}, failedChains: [], failedShown: 0, truncated: false, scope: []}
const pageOf = (page: number, total = 200) => ({results: [{message: `line-${page}`}], total, nextCursor: undefined, cursorMode: false})

function setup(running: boolean) {
    const options = {
        root: ref({id: "root", namespace: "company.team", flowId: "close_lite"}),
        scope: ref([]),
        levelParams: ref({"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"}),
        running: ref(running),
        q: ref<string>(),
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
        searchMergedLogs.mockImplementation(async ({page}) => pageOf(page))
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

    it("shouldLoadOnceMoreWhenTheExecutionFinishes", async () => {
        const {options, scope} = setup(true)
        await vi.advanceTimersByTimeAsync(0)
        searchMergedLogs.mockClear()

        options.running.value = false
        await vi.advanceTimersByTimeAsync(0)

        expect(searchMergedLogs).toHaveBeenCalledTimes(1)
        await vi.advanceTimersByTimeAsync(AUTO_REFRESH_MS * 2)
        expect(searchMergedLogs).toHaveBeenCalledTimes(1)
        scope.stop()
    })

    it("shouldKeepThePagesLoadedWithLoadMoreWhenAutoRefreshing", async () => {
        const {logs, scope} = setup(true)
        await vi.advanceTimersByTimeAsync(0)
        await logs.loadMore()
        expect(logs.lines.value.map((line) => line.message)).toEqual(["line-1", "line-2"])
        searchMergedLogs.mockClear()

        await vi.advanceTimersByTimeAsync(AUTO_REFRESH_MS)

        expect(searchMergedLogs.mock.calls.map(([search]) => search.page)).toEqual([1, 2])
        expect(logs.lines.value.map((line) => line.message)).toEqual(["line-1", "line-2"])
        scope.stop()
    })

    it("shouldAppendTheNextPageOnLoadMoreAndStopWhenEverythingIsLoaded", async () => {
        searchMergedLogs.mockImplementation(async ({page}) => pageOf(page, 2))
        const {logs, scope} = setup(false)
        await vi.advanceTimersByTimeAsync(0)
        expect(logs.hasMore.value).toBe(true)

        await logs.loadMore()

        expect(logs.lines.value).toHaveLength(2)
        expect(logs.hasMore.value).toBe(false)
        scope.stop()
    })

    it("shouldFollowTheCursorWhenTheBackendPagesByCursor", async () => {
        searchMergedLogs.mockResolvedValueOnce({results: [{message: "a"}], total: 0, nextCursor: "c1", cursorMode: true})
        searchMergedLogs.mockResolvedValueOnce({results: [{message: "b"}], total: 0, nextCursor: undefined, cursorMode: true})
        const {logs, scope} = setup(false)
        await vi.advanceTimersByTimeAsync(0)

        await logs.loadMore()

        expect(searchMergedLogs.mock.calls[1][0]).toMatchObject({cursor: "c1"})
        expect(logs.hasMore.value).toBe(false)
        scope.stop()
    })

    it("shouldIgnoreAResponseThatWasSupersededByANewerSearch", async () => {
        let releaseFirst: (value: unknown) => void = () => undefined
        searchMergedLogs.mockImplementationOnce(() => new Promise((resolve) => {
            releaseFirst = resolve
        }))
        const {options, logs, scope} = setup(false)
        await vi.advanceTimersByTimeAsync(0)

        options.levelParams.value = {"filters[level][GREATER_THAN_OR_EQUAL_TO]": "INFO"}
        await nextTick()
        await vi.advanceTimersByTimeAsync(0)
        releaseFirst({results: [{message: "stale"}], total: 1, nextCursor: undefined, cursorMode: false})
        await vi.advanceTimersByTimeAsync(0)

        expect(logs.lines.value.map((line) => line.message)).toEqual(["line-1"])
        scope.stop()
    })

    it("shouldReloadFromTheFirstPageAndPassTheTextSearchWhenItChanges", async () => {
        const {options, scope} = setup(false)
        await vi.advanceTimersByTimeAsync(0)

        options.q.value = "needle"
        await nextTick()
        await vi.advanceTimersByTimeAsync(0)

        expect(searchMergedLogs).toHaveBeenLastCalledWith(expect.objectContaining({q: "needle", page: 1}))
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
