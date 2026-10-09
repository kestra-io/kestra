import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {findTaskRunsByState, statePredicate, waitFor} from "./executionUtils"
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
            .mockResolvedValueOnce({data: {id: "e", state: "RUNNING"}})
            .mockResolvedValueOnce({data: {id: "e", state: "SUCCESS"}})
        const result = waitFor(clientReturning(get), {id: "e"}, (data) => data.state === "SUCCESS")

        await vi.advanceTimersByTimeAsync(600)

        await expect(result).resolves.toEqual({id: "e", state: "SUCCESS"})
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
        const current = {state: {histories: [{state: "CREATED"}, {state: "RUNNING"}]}}

        expect(statePredicate(execution, current)).toBe(true)
    })

    it("is true when the candidate has more histories, false when it has fewer", () => {
        const execution = {state: {histories: [{state: "CREATED"}, {state: "RUNNING"}]}} as unknown as Execution
        const more = {state: {histories: [{state: "CREATED"}, {state: "RUNNING"}, {state: "SUCCESS"}]}}
        const fewer = {state: {histories: [{state: "CREATED"}]}}

        expect(statePredicate(execution, more)).toBe(true)
        expect(statePredicate(execution, fewer)).toBe(false)
    })

    it("treats a missing histories array as length 0 on either side", () => {
        const referenceWithHistories = {state: {histories: [{state: "CREATED"}]}} as unknown as Execution
        const referenceWithoutHistories = {state: {}} as unknown as Execution

        expect(statePredicate(referenceWithHistories, {state: {}})).toBe(false)
        expect(statePredicate(referenceWithoutHistories, {state: {histories: [{state: "CREATED"}]}})).toBe(true)
        expect(statePredicate(referenceWithoutHistories, {state: {}})).toBe(true)
    })
})
