import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {dayjs} from "@kestra-io/design-system"
import type {RouteLocationNamedRaw} from "vue-router"
import {
    type CurrentStateExecution,
    findFirstFailedTaskRun, findTaskRunsByState, loopIterationsRoute, statePredicate, taskRunPath, waitFor
} from "./executionUtils"
import {queryHasTimeBound} from "../components/executions/timeRangeWiden"
import type {Execution} from "../stores/executions"

type Client = Parameters<typeof waitFor>[0]

const clientReturning = (get: ReturnType<typeof vi.fn>) => ({get} as unknown as Client)

describe("waitFor", () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("resolves with the execution once the predicate accepts it", async () => {
        const get = vi.fn()
            .mockResolvedValueOnce({data: {id: "e", state: {current: "RUNNING"}}})
            .mockResolvedValueOnce({data: {id: "e", state: {current: "SUCCESS"}}})
        const result = waitFor(clientReturning(get), {id: "e"}, (data) => data.state.current === "SUCCESS")

        await vi.advanceTimersByTimeAsync(600)

        await expect(result).resolves.toEqual({id: "e", state: {current: "SUCCESS"}})
        expect(get).toHaveBeenCalledTimes(2)
    })

    it("stops polling and resolves with the last state seen when the predicate never accepts", async () => {
        const get = vi.fn().mockResolvedValue({data: {id: "e", state: "RUNNING"}})
        const result = waitFor(clientReturning(get), {id: "e"}, () => false)

        await vi.advanceTimersByTimeAsync(300 * 100)
        await expect(result).resolves.toEqual({id: "e", state: "RUNNING"})

        await vi.advanceTimersByTimeAsync(300 * 10)
        expect(get).toHaveBeenCalledTimes(100)
    })

    it("rejects instead of hanging when a poll request fails", async () => {
        const get = vi.fn().mockRejectedValue(new Error("offline"))
        const result = waitFor(clientReturning(get), {id: "e"}, () => true)
        const rejection = expect(result).rejects.toThrow("offline")

        await vi.advanceTimersByTimeAsync(300)

        await rejection
    })
})

describe("findTaskRunsByState", () => {
    it("returns only the task runs whose state.current matches", () => {
        const execution = {
            taskRunList: [
                {id: "1", state: {current: "SUCCESS"}},
                {id: "2", state: {current: "RUNNING"}},
                {id: "3", state: {current: "SUCCESS"}},
            ],
        } as unknown as Execution

        const result = findTaskRunsByState(execution, "SUCCESS")

        expect(result).toEqual([
            {id: "1", state: {current: "SUCCESS"}},
            {id: "3", state: {current: "SUCCESS"}},
        ])
    })

    it("returns an empty array when taskRunList is missing", () => {
        const execution = {} as unknown as Execution

        expect(findTaskRunsByState(execution, "SUCCESS")).toEqual([])
    })

    it("skips task runs that have no state at all rather than throwing", () => {
        const execution = {
            taskRunList: [
                {id: "1"},
                {id: "2", state: {current: "SUCCESS"}},
            ],
        } as unknown as Execution

        expect(findTaskRunsByState(execution, "SUCCESS")).toEqual([
            {id: "2", state: {current: "SUCCESS"}},
        ])
    })
})

describe("statePredicate", () => {
    it("is true when the candidate has the same number of state histories as the reference", () => {
        const execution = {state: {histories: [{state: "CREATED"}, {state: "RUNNING"}]}} as unknown as Execution
        const current = {state: {histories: [{state: "CREATED"}, {state: "RUNNING"}]}} as unknown as CurrentStateExecution

        expect(statePredicate(execution, current)).toBe(true)
    })

    it("is true when the candidate has more histories, false when it has fewer", () => {
        const execution = {state: {histories: [{state: "CREATED"}, {state: "RUNNING"}]}} as unknown as Execution
        const more = {state: {histories: [{state: "CREATED"}, {state: "RUNNING"}, {state: "SUCCESS"}]}} as unknown as CurrentStateExecution
        const fewer = {state: {histories: [{state: "CREATED"}]}} as unknown as CurrentStateExecution

        expect(statePredicate(execution, more)).toBe(true)
        expect(statePredicate(execution, fewer)).toBe(false)
    })

    it("treats a missing histories array as length 0 on either side", () => {
        const referenceWithHistories = {state: {histories: [{state: "CREATED"}]}} as unknown as Execution
        const referenceWithoutHistories = {state: {}} as unknown as Execution
        const currentWithHistories = {state: {histories: [{state: "CREATED"}]}} as unknown as CurrentStateExecution
        const currentWithoutHistories = {state: {}} as unknown as CurrentStateExecution

        expect(statePredicate(referenceWithHistories, currentWithoutHistories)).toBe(false)
        expect(statePredicate(referenceWithoutHistories, currentWithHistories)).toBe(true)
        expect(statePredicate(referenceWithoutHistories, currentWithoutHistories)).toBe(true)
    })
})

describe("failed task lookup", () => {
    const taskRun = (id: string, current: string, extra: Record<string, unknown> = {}) =>
        ({id, taskId: id, state: {current}, ...extra})

    const execution = {
        taskRunList: [
            taskRun("per_region", "FAILED", {value: "#3 APAC"}),
            taskRun("fetch", "SUCCESS", {parentTaskRunId: "per_region"}),
            taskRun("per_quarter", "FAILED", {parentTaskRunId: "per_region", value: "#3 Q3"}),
            taskRun("reconcile", "SUCCESS"),
        ],
    } as unknown as Execution

    it("returns the failed task run that has no failed child", () => {
        // Given / When
        const failed = findFirstFailedTaskRun(execution)

        // Then
        expect(failed?.id).toBe("per_quarter")
    })

    it("returns undefined when no task run failed", () => {
        // Given
        const healthy = {taskRunList: [taskRun("fetch", "SUCCESS")]} as unknown as Execution

        // When / Then
        expect(findFirstFailedTaskRun(healthy)).toBeUndefined()
    })

    it("lists the ancestors of a task run with their loop value", () => {
        // Given
        const failed = findFirstFailedTaskRun(execution)!

        // When
        const path = taskRunPath(execution, failed)

        // Then
        expect(path).toEqual(["per_region #3 APAC", "per_quarter #3 Q3"])
    })
})

const NOW = dayjs("2026-10-09T12:00:00.000Z")

function queryFor(state?: {startDate?: string; endDate?: string}, extraQuery?: Record<string, string>) {
    return (loopIterationsRoute({id: "parent", state}, "loop", extraQuery, NOW) as RouteLocationNamedRaw).query
}

describe("loopIterationsRoute", () => {
    it("shouldBoundTheListToTheParentWindowWhenTheParentEndedBeforeTheDefaultTimeRange", () => {
        const query = queryFor({startDate: "2026-10-05T08:00:00.123456Z", endDate: "2026-10-05T08:03:00Z"}, {"filters[state][IN]": "FAILED"})

        expect(query).toEqual({
            "filters[parentId][EQUALS]": "parent",
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": "loop",
            "filters[state][IN]": "FAILED",
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-10-05T08:00:00.123Z",
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2026-10-05T08:03:00.000Z",
        })
        expect(queryHasTimeBound(query!)).toBe(true)
    })

    it.each([
        ["2026-10-09T11:00:00Z", "PT24H"],
        ["2026-10-08T06:00:00Z", "PT48H"],
        ["2026-10-05T12:00:00Z", "PT168H"],
        ["2026-08-01T12:00:00Z", "PT8760H"],
    ])("shouldPickTheSmallestRelativeRangeCoveringARunningParentStartedAt %s", (startDate, timeRange) => {
        expect(queryFor({startDate})).toMatchObject({"filters[timeRange][EQUALS]": timeRange})
    })

    it("shouldFallBackToAnOpenEndedStartDateWhenARunningParentIsOlderThanTheLargestRange", () => {
        expect(queryFor({startDate: "2025-01-01T00:00:00Z"})).toMatchObject({
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2025-01-01T00:00:00.000Z",
        })
    })

    it("shouldAddNoTimeBoundWhenTheParentStartDateIsUnknown", () => {
        expect(queryHasTimeBound(queryFor(undefined)!)).toBe(false)
    })
})
