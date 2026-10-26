import {beforeEach, describe, expect, it, vi} from "vitest"
import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
import {findFirstFailedIteration, findIterationByNumber, LoopIterationError, searchLoopIterations} from "./loopIterations"

vi.mock("@kestra-io/kestra-sdk/executions", () => ({searchExecutions: vi.fn(), execution: vi.fn()}))

const root = {id: "root", namespace: "company.team", flowId: "close_lite", startDate: "2026-10-09T09:49:13.994699Z"}

describe("searchLoopIterations", () => {
    beforeEach(() => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockClear()
        vi.mocked(ExecutionsAPI.searchExecutions).mockResolvedValue({results: [], total: 0} as never)
    })

    it("shouldBoundTheSearchByTheRootEndDateWhenTheRootHasFinished", async () => {
        await searchLoopIterations({root: {...root, endDate: "2026-10-09T09:49:17.019006Z"}, state: "FAILED"})

        const {filters} = vi.mocked(ExecutionsAPI.searchExecutions).mock.calls[0][0] as {filters: {field: string; operation: string; value: string}[]}
        expect(filters).toContainEqual({field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: "2026-10-09T09:49:18Z"})
    })

    it("shouldWidenTheStartBoundToTheSecondSoAnIterationStartedInTheSameSecondAsTheRootIsKept", async () => {
        await searchLoopIterations({root, state: "FAILED"})

        const {filters} = vi.mocked(ExecutionsAPI.searchExecutions).mock.calls[0][0] as {filters: {field: string; operation: string; value: string}[]}
        expect(filters).toContainEqual({field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: "2026-10-09T09:49:13Z"})
    })

    it("shouldRoundTheEndBoundUpWhenTheRootEndedWithinAFractionOfASecondBelowMillisecondPrecision", async () => {
        await searchLoopIterations({root: {...root, endDate: "2026-10-09T09:49:17.000400Z"}, state: "FAILED"})

        const {filters} = vi.mocked(ExecutionsAPI.searchExecutions).mock.calls[0][0] as {filters: {field: string; value: string}[]}
        expect(filters.find((filter) => filter.field === "endDate")?.value).toBe("2026-10-09T09:49:18Z")
    })

    it("shouldKeepTheEndBoundWhenTheRootEndedOnAWholeSecond", async () => {
        await searchLoopIterations({root: {...root, endDate: "2026-10-09T09:49:17.000000Z"}, state: "FAILED"})

        const {filters} = vi.mocked(ExecutionsAPI.searchExecutions).mock.calls[0][0] as {filters: {field: string; value: string}[]}
        expect(filters.find((filter) => filter.field === "endDate")?.value).toBe("2026-10-09T09:49:17Z")
    })

    it("shouldNotBoundTheSearchByAnEndDateWhileTheRootRuns", async () => {
        await searchLoopIterations({root, state: "FAILED"})

        const {filters} = vi.mocked(ExecutionsAPI.searchExecutions).mock.calls[0][0] as {filters: {field: string}[]}
        expect(filters.some((filter) => filter.field === "endDate")).toBe(false)
    })

    it("shouldExposeTheStartAndEndDatesOfEachIteration", async () => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockResolvedValue({
            results: [{id: "customer-13", state: {current: "SUCCESS", startDate: "2026-10-09T09:49:14.1Z", endDate: "2026-10-09T09:49:16.2Z"}, loopRun: {taskId: "per_customer", index: 12}}],
            total: 1,
        } as never)

        const {results} = await searchLoopIterations({parentId: "root"})

        expect(results[0]).toMatchObject({startDate: "2026-10-09T09:49:14.1Z", endDate: "2026-10-09T09:49:16.2Z"})
    })

    it("shouldSortByIndexThenIdSoPagesStayStableWhenSeveralRunsShareAnIndex", async () => {
        await searchLoopIterations({parentId: "host", taskId: "per_region"})

        expect(vi.mocked(ExecutionsAPI.searchExecutions).mock.calls[0][0]).toMatchObject({sort: ["loopRunIndex:asc", "id:asc"]})
    })
})

const row = (id: string, index: number, taskRunId: string, state = "SUCCESS") => ({
    id,
    state: {current: state},
    parentId: "host",
    loopRun: {taskId: "per_region", taskRunId, index, value: `v${index}`},
})

describe("findIterationByNumber", () => {
    beforeEach(() => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockClear()
    })

    it("shouldReturnTheIterationOfTheRequestedTaskRunWhenTwoRunsShareTheNumber", async () => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockResolvedValue({
            results: [row("a-1", 0, "tr-1"), row("b-1", 0, "tr-2")],
            total: 2,
        } as never)

        const iteration = await findIterationByNumber("host", "per_region", 1, "tr-2")

        expect(iteration?.id).toBe("b-1")
    })

    it("shouldKeepPagingUntilTheRequestedTaskRunIterationWhenRunsInterleave", async () => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockImplementation((async ({page, size}: {page: number; size: number}) => {
            const all = Array.from({length: 120}, (_, position) => row(`it-${position}`, Math.floor(position / 2), position % 2 === 0 ? "tr-1" : "tr-2"))
            return {results: all.slice((page - 1) * size, page * size), total: all.length}
        }) as never)

        const iteration = await findIterationByNumber("host", "per_region", 30, "tr-2")

        expect(iteration?.id).toBe("it-59")
        expect(vi.mocked(ExecutionsAPI.searchExecutions).mock.calls.map(([params]) => params.page)).toEqual([1, 2])
    })

    it("shouldNotLookPastThePageHoldingTheNumberWhenASingleRunOwnsIt", async () => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockImplementation((async ({page, size}: {page: number; size: number}) => {
            const all = Array.from({length: 120}, (_, index) => row(`it-${index}`, index, "tr-1"))
            return {results: all.slice((page - 1) * size, page * size), total: all.length}
        }) as never)

        const iteration = await findIterationByNumber("host", "per_region", 30, "tr-1")

        expect(iteration?.id).toBe("it-29")
        expect(ExecutionsAPI.searchExecutions).toHaveBeenCalledTimes(1)
    })

    it("shouldRefuseToChooseWhenNoTaskRunIsGivenAndSeveralRunsShareTheNumber", async () => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockResolvedValue({
            results: [row("a-1", 0, "tr-1"), row("b-1", 0, "tr-2")],
            total: 2,
        } as never)

        await expect(findIterationByNumber("host", "per_region", 1)).rejects.toEqual(new LoopIterationError("ambiguous"))
    })

    it("shouldReturnTheIterationWhenNoTaskRunIsGivenAndOnlyOneRunHasTheNumber", async () => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockResolvedValue({
            results: [row("a-1", 0, "tr-1"), row("a-2", 1, "tr-1"), row("b-1", 0, "tr-2")].sort((a, b) => a.loopRun.index - b.loopRun.index),
            total: 3,
        } as never)

        expect((await findIterationByNumber("host", "per_region", 2))?.id).toBe("a-2")
    })

    it("shouldReturnNothingWhenTheNumberDoesNotExist", async () => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockResolvedValue({results: [row("a-1", 0, "tr-1")], total: 1} as never)

        expect(await findIterationByNumber("host", "per_region", 98, "tr-1")).toBeUndefined()
    })
})

describe("findFirstFailedIteration", () => {
    it("shouldSkipFailuresOfOtherTaskRunsEvenPastThePageOfThem", async () => {
        vi.mocked(ExecutionsAPI.searchExecutions).mockClear()
        vi.mocked(ExecutionsAPI.searchExecutions).mockImplementation((async ({page, size}: {page: number; size: number}) => {
            const all = [...Array.from({length: 10}, (_, index) => row(`other-${index}`, index, "tr-1", "FAILED")), row("ours", 10, "tr-2", "FAILED")]
            return {results: all.slice((page - 1) * size, page * size), total: all.length}
        }) as never)

        const failed = await findFirstFailedIteration("host", "per_region", "tr-2")

        expect(failed?.id).toBe("ours")
    })
})
