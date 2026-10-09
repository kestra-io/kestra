import {describe, expect, it} from "vitest"
import {findTaskRunsByState, statePredicate} from "../../../src/utils/executionUtils"
import type {Execution} from "../../../src/stores/executions"

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
