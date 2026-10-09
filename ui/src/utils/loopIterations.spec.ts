import {describe, expect, it} from "vitest"
import {resolveIterationChain, type IterationParent} from "./loopIterations"

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
