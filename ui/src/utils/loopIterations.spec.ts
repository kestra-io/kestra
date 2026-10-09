import {describe, expect, it, vi} from "vitest"
import {findFailedIterationChain, resolveIterationChain, type IterationParent} from "./loopIterations"

const executions: Record<string, IterationParent> = {
    "outer-iteration-13": {parentId: "root", loopRun: {taskId: "per_customer", index: 12}},
    "stranger-iteration": {parentId: "other-root", loopRun: {taskId: "per_customer", index: 2}},
}
const fetchParent = async (id: string) => executions[id]

const candidate = (parentId: string) => ({id: "failed-inner", number: 7, state: "FAILED", taskId: "per_invoice", parentId})

describe("resolveIterationChain", () => {
    it("shouldBuildTheScopeFromAFailedNestedIterationUnderASuccessfulParent", async () => {
        const chain = await resolveIterationChain(candidate("outer-iteration-13"), "root", fetchParent)

        expect(chain).toEqual({
            entries: [{taskId: "per_customer", number: 13}, {taskId: "per_invoice", number: 7}],
            leafId: "failed-inner",
        })
    })

    it("shouldReturnASingleEntryWhenTheCandidateHangsDirectlyOffTheRoot", async () => {
        const chain = await resolveIterationChain(candidate("root"), "root", fetchParent)

        expect(chain?.entries).toEqual([{taskId: "per_invoice", number: 7}])
    })

    it("shouldRejectACandidateWhoseChainReachesAnotherRoot", async () => {
        expect(await resolveIterationChain(candidate("stranger-iteration"), "root", fetchParent)).toBeUndefined()
    })

    it("shouldRejectACandidateWhoseParentIsNotALoopIteration", async () => {
        expect(await resolveIterationChain(candidate("unknown"), "root", fetchParent)).toBeUndefined()
    })
})

describe("findFailedIterationChain", () => {
    const root = {id: "root", namespace: "company.team", flowId: "close_lite", startDate: "2026-10-09T09:49:13.994699Z"}
    const failed = (id: string, parentId: string, number: number) => ({id, number, state: "FAILED", parentId})

    it("shouldBoundTheSearchByTheRootAndSkipCandidatesFromAnotherExecutionOfTheSameFlow", async () => {
        const search = vi.fn().mockResolvedValue({
            results: [failed("other-run-failure", "stranger-iteration", 3), failed("our-failure", "outer-iteration-13", 7)],
            total: 2,
        })
        const fetchSpy = vi.fn(async (id: string) => executions[id])

        const chain = await findFailedIterationChain(root, "per_invoice", {search, fetchParent: fetchSpy})

        expect(search).toHaveBeenCalledWith(expect.objectContaining({root, taskId: "per_invoice", state: "FAILED"}))
        expect(chain).toEqual({
            entries: [{taskId: "per_customer", number: 13}, {taskId: "per_invoice", number: 7}],
            leafId: "our-failure",
        })
    })

    it("shouldFetchEachSharedParentOnce", async () => {
        const search = vi.fn().mockResolvedValue({
            results: [failed("a", "outer-iteration-13", 1), failed("b", "outer-iteration-13", 2)],
            total: 2,
        })
        const fetchSpy = vi.fn(async (id: string) => executions[id])

        await findFailedIterationChain(root, "per_invoice", {search, fetchParent: fetchSpy})

        expect(fetchSpy).toHaveBeenCalledTimes(1)
    })

    it("shouldReturnNothingWhenNoCandidateReachesTheRoot", async () => {
        const search = vi.fn().mockResolvedValue({results: [failed("x", "stranger-iteration", 1)], total: 1})

        expect(await findFailedIterationChain(root, "per_invoice", {search, fetchParent: async (id) => executions[id]})).toBeUndefined()
    })
})
