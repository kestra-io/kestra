import {describe, expect, it} from "vitest"
import {ambiguousValues, iterationLabel, parseLoopScope, scopedExecutionId, serializeLoopScope, withScopedIteration} from "./loopScope"

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

    it("shouldWriteTheZeroBasedIndexAndOmitAnEmptyScope", () => {
        expect(serializeLoopScope([{taskId: "a", number: 3}, {taskId: "b", number: 1}])).toBe("a@2,b@0")
        expect(serializeLoopScope([])).toBeUndefined()
    })

    it("shouldReadTheZeroBasedIndexAndKeepReadingTheOneBasedLinks", () => {
        expect(parseLoopScope("a@2,b@0")).toEqual([{taskId: "a", number: 3}, {taskId: "b", number: 1}])
        expect(parseLoopScope("a:3,b@0")).toEqual([{taskId: "a", number: 3}, {taskId: "b", number: 1}])
        expect(parseLoopScope(serializeLoopScope([{taskId: "a", number: 7}]))).toEqual([{taskId: "a", number: 7}])
        expect(parseLoopScope("a@-1")).toEqual([])
        expect(parseLoopScope("a@")).toEqual([])
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

describe("iterationLabel", () => {
    const t = (key: string, named?: Record<string, unknown>) => `${key.split(".").pop()}(${Object.values(named ?? {}).join(",")})`

    it("shouldLabelAnIterationByItsValueWithoutAnyNumber", () => {
        expect(iterationLabel(t, 3, "AMER")).toBe("AMER")
    })

    it("shouldFallBackToTheZeroBasedIndexWhenTheValueIsEmpty", () => {
        expect(iterationLabel(t, 3, undefined)).toBe("iteration-index(2)")
        expect(iterationLabel(t, 3, "  ")).toBe("iteration-index(2)")
    })

    it("shouldAddTheZeroBasedIndexToAnAmbiguousValue", () => {
        expect(iterationLabel(t, 3, "AMER", true)).toBe("iteration-value-index(AMER,2)")
    })
})

describe("ambiguousValues", () => {
    it("shouldFlagAValueSharedByTwoIterationsOfTheSameLoopRun", () => {
        const isAmbiguous = ambiguousValues([
            {id: "1", taskId: "per_region", value: "AMER"},
            {id: "2", taskId: "per_region", value: "AMER"},
            {id: "3", taskId: "per_region", value: "EMEA"},
            {id: "4", parentId: "x", taskId: "per_quarter", value: "Q1"},
            {id: "5", parentId: "y", taskId: "per_quarter", value: "Q1"},
        ])

        expect(isAmbiguous({taskId: "per_region", value: "AMER"})).toBe(true)
        expect(isAmbiguous({taskId: "per_region", value: "EMEA"})).toBe(false)
        expect(isAmbiguous({parentId: "x", taskId: "per_quarter", value: "Q1"})).toBe(false)
    })
})
