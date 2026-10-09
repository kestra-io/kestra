import {describe, expect, it, vi} from "vitest"
import {
    CHAIN_CONCURRENCY,
    collectFailedTargets,
    collectScopedTargets,
    createChainCache,
    executionIdsUrlLength,
    flowHasLoop,
    logUrlReserve,
    scopeLabel,
    type LogTargetDeps,
} from "./loopLogScope"
import type {LoopIteration} from "./loopIterations"

const root = {id: "root", namespace: "company.team", flowId: "close_lite", startDate: "2026-10-09T09:49:13.994699Z"}

const iteration = (id: string, parentId: string, taskId: string, index: number, parent?: LoopIteration["loopRun"]): LoopIteration => ({
    id,
    parentId,
    taskId,
    number: index + 1,
    value: String(index + 1),
    state: "FAILED",
    loopRun: {taskId, index, value: String(index + 1), parent: parent?.parent},
})

const embeddedParent = (id: string, parentId: string, taskId: string, index: number) => ({
    id,
    parentId,
    loopRun: {taskId, index, value: String(index + 1)},
})

const depsFor = (topLevel: LoopIteration[], anyDepth: LoopIteration[], parents: Record<string, unknown> = {}): LogTargetDeps & {fetchIteration: ReturnType<typeof vi.fn>} => ({
    search: vi.fn(async (search) => {
        const results = search.parentId ? topLevel : anyDepth
        return {results, total: results.length}
    }),
    fetchIteration: vi.fn(async (id: string) => parents[id] as never),
    findByNumber: vi.fn(),
})

const nestedFailure = (id: string, customerIndex: number) => ({
    ...iteration(id, `customer-${customerIndex + 1}`, "per_invoice", 6),
    loopRun: {taskId: "per_invoice", index: 6, value: "7", parent: embeddedParent(`customer-${customerIndex + 1}`, "root", "per_customer", customerIndex)},
})

describe("collectFailedTargets", () => {
    it("shouldIncludeTopLevelFailedIterationsWithTheRootOnly", async () => {
        const deps = depsFor([iteration("amer", "root", "per_region", 1)], [iteration("amer", "root", "per_region", 1)])

        const targets = await collectFailedTargets(root, deps)

        expect(targets.executionIds).toEqual(["root", "amer"])
        expect(targets.chains["amer"]).toEqual([{id: "amer", taskId: "per_region", number: 2, value: "2"}])
        expect(targets.truncated).toBe(false)
    })

    it("shouldIncludeNestedFailuresUnderSuccessfulParentsWithTheirAncestors", async () => {
        const failures = [nestedFailure("f13", 12), nestedFailure("f26", 25), nestedFailure("f39", 38)]
        const deps = depsFor([], failures)

        const targets = await collectFailedTargets(root, deps)

        expect(targets.executionIds).toEqual(["root", "customer-13", "f13", "customer-26", "f26", "customer-39", "f39"])
        expect(targets.chains["f26"].map((node) => `${node.taskId}:${node.number}`)).toEqual(["per_customer:26", "per_invoice:7"])
        expect(targets.failedShown).toBe(3)
        expect(deps.fetchIteration).not.toHaveBeenCalled()
    })

    it("shouldFetchAParentThatIsNotEmbeddedOnlyOnce", async () => {
        const bare = (id: string) => ({...iteration(id, "customer-13", "per_invoice", 6), loopRun: {taskId: "per_invoice", index: 6}})
        const deps = depsFor([], [bare("a"), bare("b")], {"customer-13": embeddedParent("customer-13", "root", "per_customer", 12)})

        const targets = await collectFailedTargets(root, deps)

        expect(deps.fetchIteration).toHaveBeenCalledTimes(1)
        expect(targets.executionIds).toEqual(["root", "customer-13", "a", "b"])
    })

    it("shouldDropFailuresOfAnotherExecutionOfTheSameFlow", async () => {
        const stranger = {...iteration("s", "other-iteration", "per_invoice", 1), loopRun: {taskId: "per_invoice", index: 1, parent: {id: "other-iteration", parentId: "other-root", loopRun: {taskId: "per_customer", index: 0}}}}
        const deps = depsFor([], [stranger, nestedFailure("f13", 12)], {"other-root": {id: "other-root"}})

        const targets = await collectFailedTargets(root, deps)

        expect(targets.executionIds).toEqual(["root", "customer-13", "f13"])
    })

    it("shouldStopAtTheExecutionIdBudgetAndFlagTheTruncation", async () => {
        const failures = Array.from({length: 10}, (_, index) => iteration(`f${index}`, "root", "per_region", index))
        const deps = depsFor(failures, failures)

        const targets = await collectFailedTargets(root, deps, {limit: executionIdsUrlLength(["root", "f0", "f1", "f2"])})

        expect(targets.executionIds).toEqual(["root", "f0", "f1", "f2"])
        expect(targets.failedShown).toBe(3)
        expect(targets.truncated).toBe(true)
    })

    it("shouldFlagTruncationWhenTheSearchTotalExceedsWhatCameBack", async () => {
        const deps = depsFor([], [nestedFailure("f13", 12)])
        deps.search = vi.fn(async () => ({results: [nestedFailure("f13", 12)], total: 500}))

        const targets = await collectFailedTargets(root, deps)

        expect(targets.truncated).toBe(true)
    })
})

describe("collectScopedTargets", () => {
    const customer = async (_parent: string, taskId: string, number: number) => ({id: `customer-${number}`, number, value: String(number), state: "SUCCESS", taskId})
    const childOf = (id: string, parentId: string, index: number) => iteration(id, parentId, "per_invoice", index)

    it("shouldSearchTheFailedChildrenOfTheDeepestScopeEvenWhenTheyAreOutsideTheGlobalFirstPage", async () => {
        const deps = depsFor([], [])
        deps.search = vi.fn(async (search) => {
            const results = search.parentId === "customer-13" ? [childOf("f13", "customer-13", 6)] : []
            return {results, total: results.length}
        })
        deps.findByNumber = vi.fn(customer)

        const targets = await collectScopedTargets(root, [{taskId: "per_customer", number: 13}], deps)

        expect(targets.executionIds).toEqual(["root", "customer-13", "f13"])
        expect(targets.chains["f13"].map((node) => `${node.taskId}:${node.number}`)).toEqual(["per_customer:13", "per_invoice:7"])
        expect(targets.scope).toEqual([{id: "customer-13", taskId: "per_customer", number: 13, value: "13"}])
        expect(targets.truncated).toBe(false)
    })

    it("shouldRecurseThroughFailedChildren", async () => {
        const deps = depsFor([], [])
        deps.search = vi.fn(async (search) => {
            const byParent: Record<string, LoopIteration[]> = {
                "customer-13": [childOf("inv-7", "customer-13", 6)],
                "inv-7": [iteration("line-2", "inv-7", "per_line", 1)],
            }
            const results = byParent[search.parentId ?? ""] ?? []
            return {results, total: results.length}
        })
        deps.findByNumber = vi.fn(customer)

        const targets = await collectScopedTargets(root, [{taskId: "per_customer", number: 13}], deps)

        expect(targets.executionIds).toEqual(["root", "customer-13", "inv-7", "line-2"])
    })

    it("shouldFlagTruncationWhenTheChildrenSearchHasMoreResultsThanItReturned", async () => {
        const deps = depsFor([], [])
        deps.search = vi.fn(async () => ({results: [childOf("f13", "customer-13", 6)], total: 900}))
        deps.findByNumber = vi.fn(customer)

        const targets = await collectScopedTargets(root, [{taskId: "per_customer", number: 13}], deps)

        expect(targets.truncated).toBe(true)
    })

    it("shouldRejectAScopeThatNoLongerExists", async () => {
        const deps = depsFor([], [])
        deps.findByNumber = vi.fn(async () => undefined)

        await expect(collectScopedTargets(root, [{taskId: "per_customer", number: 99}], deps)).rejects.toMatchObject({failure: "not-found"})
    })
})

describe("chain resolution", () => {
    const bare = (id: string, parent: string) => ({...iteration(id, parent, "per_invoice", 6), loopRun: {taskId: "per_invoice", index: 6}})

    it("shouldNeverResolveMoreChainsAtOnceThanTheConcurrencyLimit", async () => {
        let active = 0
        let peak = 0
        const candidates = Array.from({length: 20}, (_, index) => bare(`i${index}`, `p${index}`))
        const deps = depsFor([], candidates)
        deps.fetchIteration = vi.fn(async (id: string) => {
            active++
            peak = Math.max(peak, active)
            await new Promise((resolve) => setTimeout(resolve, 1))
            active--
            return embeddedParent(id, "root", "per_customer", 1) as never
        })

        await collectFailedTargets(root, deps)

        expect(peak).toBeLessThanOrEqual(CHAIN_CONCURRENCY)
        expect(peak).toBeGreaterThan(1)
    })

    it("shouldNotResolveAKnownChainAgainOnTheNextCollection", async () => {
        const candidates = [bare("a", "customer-13")]
        const deps = depsFor([], candidates, {"customer-13": embeddedParent("customer-13", "root", "per_customer", 12)})
        const cache = createChainCache()

        await collectFailedTargets(root, deps, {cache})
        await collectFailedTargets(root, deps, {cache})

        expect(deps.fetchIteration).toHaveBeenCalledTimes(1)
    })
})

describe("logUrlReserve", () => {
    it("shouldGrowWithTheLevelFilterAndTheTextSearch", () => {
        const base = {levelParams: {"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"}, kinds: ["NORMAL", "LOOP"]}

        expect(logUrlReserve({...base, q: "a long search text"})).toBeGreaterThan(logUrlReserve(base))
    })

    it("shouldKeepTheFullUrlUnderTheLimitWithTheCursorMargin", async () => {
        const failures = Array.from({length: 300}, (_, index) => iteration(`${"x".repeat(22)}${index}`, "root", "per_region", index))
        const deps = depsFor(failures, failures)
        const reserve = logUrlReserve({levelParams: {"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"}, kinds: ["NORMAL", "LOOP"], q: "needle"})

        const targets = await collectFailedTargets(root, deps, {reserve})

        expect(reserve + executionIdsUrlLength(targets.executionIds)).toBeLessThanOrEqual(3500)
        expect(targets.truncated).toBe(true)
    })
})

describe("scopeLabel", () => {
    const t = (key: string, named?: Record<string, unknown>) => key === "topology-graph.loop.iteration-label" ? `#${named?.number} ${named?.value}` : `Iteration ${named?.number}`

    it("shouldJoinEachLoopWithItsIterationLabel", () => {
        const chain = [{id: "a", taskId: "per_customer", number: 13, value: "13"}, {id: "b", taskId: "per_invoice", number: 7, value: "7"}]

        expect(scopeLabel(t, chain)).toBe("per_customer: #13 13 › per_invoice: #7 7")
    })

    it("shouldFallBackToTheIterationNumberWhenThereIsNoValue", () => {
        expect(scopeLabel(t, [{id: "a", taskId: "per_customer", number: 3}])).toBe("per_customer: Iteration 3")
    })
})

describe("flowHasLoop", () => {
    it("shouldFindALoopNestedInsideAnotherTask", () => {
        const flow = {tasks: [{id: "seq", type: "io.kestra.plugin.core.flow.Sequential", tasks: [{id: "l", type: "io.kestra.plugin.core.flow.Loop"}]}]}

        expect(flowHasLoop(flow)).toBe(true)
    })

    it("shouldReturnFalseForAFlowWithoutLoop", () => {
        expect(flowHasLoop({tasks: [{id: "log", type: "io.kestra.plugin.core.log.Log"}]})).toBe(false)
        expect(flowHasLoop(undefined)).toBe(false)
    })
})
