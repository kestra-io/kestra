import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import {useLoopIterations, LOOP_ITERATIONS_PAGE_SIZE} from "../../../src/composables/useLoopIterations"
import {useExecutionsStore} from "../../../src/stores/executions"

function makeExecution(id: string, value: string, index: number, state = "SUCCESS") {
    return {
        id,
        loopRun: {index, value},
        state: {
            current: state,
            histories: [
                {state: "RUNNING", date: "2026-01-01T00:00:00Z"},
                {state, date: "2026-01-01T00:00:01Z"},
            ],
        },
    }
}

describe("useLoopIterations", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("does not fetch until ensureLoaded is called", () => {
        const store = useExecutionsStore()
        const findExecutions = vi.spyOn(store, "findExecutions")

        useLoopIterations("parent-1", "loopTask")

        expect(findExecutions).not.toHaveBeenCalled()
    })

    it("loads page 1 on first ensureLoaded call", async () => {
        const store = useExecutionsStore()
        vi.spyOn(store, "findExecutions").mockResolvedValue({
            results: [makeExecution("it-1", "EMEA", 0)],
            total: 1,
        } as never)

        const {iterations, total, loaded, ensureLoaded} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()

        expect(iterations.value).toHaveLength(1)
        expect(iterations.value[0].value).toBe("EMEA")
        expect(total.value).toBe(1)
        expect(loaded.value).toBe(true)
    })

    it("is a no-op on a second ensureLoaded call once already loaded", async () => {
        const store = useExecutionsStore()
        const findExecutions = vi.spyOn(store, "findExecutions").mockResolvedValue({
            results: [makeExecution("it-1", "EMEA", 0)],
            total: 1,
        } as never)

        const {ensureLoaded} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()
        await ensureLoaded()

        expect(findExecutions).toHaveBeenCalledTimes(1)
    })

    it("needsPreview is false at exactly the page size", async () => {
        const store = useExecutionsStore()
        vi.spyOn(store, "findExecutions").mockResolvedValue({
            results: Array.from({length: LOOP_ITERATIONS_PAGE_SIZE}, (_, i) => makeExecution(`it-${i}`, `V${i}`, i)),
            total: LOOP_ITERATIONS_PAGE_SIZE,
        } as never)

        const {needsPreview, ensureLoaded} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()

        expect(needsPreview.value).toBe(false)
    })

    it("needsPreview is true just above the page size", async () => {
        const store = useExecutionsStore()
        vi.spyOn(store, "findExecutions").mockResolvedValue({
            results: Array.from({length: LOOP_ITERATIONS_PAGE_SIZE}, (_, i) => makeExecution(`it-${i}`, `V${i}`, i)),
            total: LOOP_ITERATIONS_PAGE_SIZE + 1,
        } as never)

        const {needsPreview, ensureLoaded} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()

        expect(needsPreview.value).toBe(true)
    })

    it("loadMore appends page 2 without dropping page 1's rows", async () => {
        const store = useExecutionsStore()
        const findExecutions = vi.spyOn(store, "findExecutions")
        findExecutions.mockResolvedValueOnce({
            results: [makeExecution("it-1", "P1", 0)],
            total: 2,
        } as never)
        findExecutions.mockResolvedValueOnce({
            results: [makeExecution("it-2", "P2", 1)],
            total: 2,
        } as never)

        const {iterations, hasMore, ensureLoaded, loadMore} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()
        expect(hasMore.value).toBe(true)

        await loadMore()

        expect(iterations.value.map(i => i.id)).toEqual(["it-1", "it-2"])
        expect(findExecutions).toHaveBeenCalledTimes(2)
    })

    it("loadMore is a no-op once hasMore is false", async () => {
        const store = useExecutionsStore()
        const findExecutions = vi.spyOn(store, "findExecutions").mockResolvedValue({
            results: [makeExecution("it-1", "EMEA", 0)],
            total: 1,
        } as never)

        const {ensureLoaded, loadMore} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()
        await loadMore()

        expect(findExecutions).toHaveBeenCalledTimes(1)
    })

    it("setFailedOnly resets pagination and refetches with the state filter", async () => {
        const store = useExecutionsStore()
        const findExecutions = vi.spyOn(store, "findExecutions")
        findExecutions.mockResolvedValueOnce({
            results: [makeExecution("it-1", "P1", 0), makeExecution("it-2", "P2", 1)],
            total: 2,
        } as never)
        findExecutions.mockResolvedValueOnce({
            results: [makeExecution("it-2", "P2", 1, "FAILED")],
            total: 1,
        } as never)

        const {iterations, ensureLoaded, setFailedOnly} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()
        expect(iterations.value).toHaveLength(2)

        await setFailedOnly(true)

        expect(iterations.value).toHaveLength(1)
        expect(iterations.value[0].id).toBe("it-2")

        const secondCallArgs = findExecutions.mock.calls[1][0] as Record<string, unknown>
        expect(secondCallArgs["filters[state][IN]"]).toEqual(["FAILED"])
        expect(secondCallArgs.page).toBe(1)
    })

        it("ignores a stale in-flight fetch when failedOnly changes before it resolves", async () => {
        const store = useExecutionsStore()
        let resolveFirst: (value: unknown) => void
        const firstFetch = new Promise((resolve) => {
            resolveFirst = resolve
        })

        const findExecutions = vi.spyOn(store, "findExecutions")
        findExecutions.mockImplementationOnce(() => firstFetch as never)
        findExecutions.mockResolvedValueOnce({
            results: [makeExecution("it-2", "FAILED-ONLY", 0, "FAILED")],
            total: 1,
        } as never)

        const {iterations, total, setFailedOnly, ensureLoaded} = useLoopIterations("parent-1", "loopTask")

        const firstLoad = ensureLoaded()
        const secondLoad = setFailedOnly(true)

        // Resolve the first (now-stale) fetch after the second has already started.
        resolveFirst!({results: [makeExecution("it-1", "UNFILTERED", 0)], total: 1})

        await Promise.all([firstLoad, secondLoad])

        expect(iterations.value).toHaveLength(1)
        expect(iterations.value[0].id).toBe("it-2")
        expect(total.value).toBe(1)
    })

    it("retry re-fetches the page that failed, not always page 1", async () => {
        const store = useExecutionsStore()
        const findExecutions = vi.spyOn(store, "findExecutions")
        findExecutions.mockResolvedValueOnce({
            results: [makeExecution("it-1", "P1", 0)],
            total: 2,
        } as never)
        findExecutions.mockRejectedValueOnce(new Error("page 2 failed"))
        findExecutions.mockResolvedValueOnce({
            results: [makeExecution("it-2", "P2", 1)],
            total: 2,
        } as never)

        const {iterations, error, ensureLoaded, loadMore, retry} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()
        await loadMore()

        expect(error.value).toBeDefined()
        expect(iterations.value).toHaveLength(1)

        await retry()

        expect(error.value).toBeUndefined()
        expect(iterations.value.map(i => i.id)).toEqual(["it-1", "it-2"])

        const retryCallArgs = findExecutions.mock.calls[2][0] as Record<string, unknown>
        expect(retryCallArgs.page).toBe(2)
    })

    it("retry re-fetches page 1 when the very first load failed", async () => {
        const store = useExecutionsStore()
        const findExecutions = vi.spyOn(store, "findExecutions")
        findExecutions.mockRejectedValueOnce(new Error("first load failed"))
        findExecutions.mockResolvedValueOnce({
            results: [makeExecution("it-1", "EMEA", 0)],
            total: 1,
        } as never)

        const {iterations, error, loaded, ensureLoaded, retry} = useLoopIterations("parent-1", "loopTask")
        await ensureLoaded()

        expect(error.value).toBeDefined()
        expect(loaded.value).toBe(false)

        await retry()

        expect(error.value).toBeUndefined()
        expect(iterations.value).toHaveLength(1)

        const retryCallArgs = findExecutions.mock.calls[1][0] as Record<string, unknown>
        expect(retryCallArgs.page).toBe(1)
    })

    it("keeps parentId, kind and taskId filters on every fetch", async () => {
        const store = useExecutionsStore()
        const findExecutions = vi.spyOn(store, "findExecutions").mockResolvedValue({
            results: [],
            total: 0,
        } as never)

        const {ensureLoaded} = useLoopIterations("parent-42", "myLoopTask")
        await ensureLoaded()

        const args = findExecutions.mock.calls[0][0] as Record<string, unknown>
        expect(args["filters[parentId][EQUALS]"]).toBe("parent-42")
        expect(args["filters[kind][EQUALS]"]).toBe("LOOP")
        expect(args["filters[taskId][EQUALS]"]).toBe("myLoopTask")
        expect(args.commit).toBe(false)
    })
})
