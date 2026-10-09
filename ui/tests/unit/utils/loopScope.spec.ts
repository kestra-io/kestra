import {describe, expect, it} from "vitest"
import {parseLoopScope, scopedExecutionId, serializeLoopScope, withScopedIteration} from "../../../src/utils/loopScope"

describe("loop scope query", () => {
    it("shouldParseNestedScopesInOrder", () => {
        expect(parseLoopScope("per_region:1,per_quarter:2")).toEqual([
            {taskId: "per_region", number: 1},
            {taskId: "per_quarter", number: 2},
        ])
    })

    it("shouldDropEverythingFromTheFirstMalformedEntry", () => {
        expect(parseLoopScope("per_region:1,per_quarter:x,other:3")).toEqual([{taskId: "per_region", number: 1}])
        expect(parseLoopScope("per_region:0")).toEqual([])
        expect(parseLoopScope(":2")).toEqual([])
        expect(parseLoopScope(undefined)).toEqual([])
        expect(parseLoopScope(["per_region:2"])).toEqual([{taskId: "per_region", number: 2}])
    })

    it("shouldRoundTripAndOmitAnEmptyScope", () => {
        expect(serializeLoopScope([{taskId: "a", number: 3}, {taskId: "b", number: 1}])).toBe("a:3,b:1")
        expect(serializeLoopScope([])).toBeUndefined()
    })

    it("shouldDropDeeperScopesWhenAnOuterIterationChanges", () => {
        const entries = parseLoopScope("per_region:1,per_quarter:2")

        expect(withScopedIteration(entries, 0, "per_region", 5)).toEqual([{taskId: "per_region", number: 5}])
        expect(withScopedIteration(entries, 1, "per_quarter", 4)).toEqual([
            {taskId: "per_region", number: 1},
            {taskId: "per_quarter", number: 4},
        ])
    })
})

describe("scopedExecutionId", () => {
    const scoped = {per_region: {id: "iter-region"}, "per_region.per_quarter": {id: "iter-quarter"}}

    it("shouldRewriteNodesUnderTheScopedLoopOnly", () => {
        expect(scopedExecutionId("per_region.upload", scoped)).toBe("iter-region")
        expect(scopedExecutionId("per_region", scoped)).toBeUndefined()
        expect(scopedExecutionId("per_regional.upload", scoped)).toBeUndefined()
        expect(scopedExecutionId("write_report", scoped)).toBeUndefined()
    })

    it("shouldPreferTheDeepestScopedLoop", () => {
        expect(scopedExecutionId("per_region.per_quarter.upload", scoped)).toBe("iter-quarter")
    })
})
