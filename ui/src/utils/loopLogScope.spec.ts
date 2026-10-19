import {beforeAll, beforeEach, describe, expect, it, vi} from "vitest"
import {configureClient} from "@kestra-io/kestra-sdk"
import {client} from "@kestra-io/kestra-sdk/client"
import {
    CHAIN_CONCURRENCY,
    collectFailedTargets,
    collectScopedTargets,
    createChainCache,
    executionIdsUrlLength,
    flowHasLoop,
    LOG_URL_MARGIN,
    logUrlReserve,
    MAX_LOG_URL_LENGTH,
    scopeLabel,
    searchMergedLogs,
    type LogTargetDeps,
} from "./loopLogScope"
import type {LoopIteration} from "./loopIterations"

beforeAll(() => {
    configureClient({baseUrl: "http://localhost"})
})

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

const pageOf = (items: LoopIteration[], search: {page?: number; size?: number}) => {
    const size = search.size ?? 100
    const start = ((search.page ?? 1) - 1) * size
    return {results: items.slice(start, start + size), total: items.length}
}

const depsFor = (failures: LoopIteration[], parents: Record<string, unknown> = {}): LogTargetDeps & {fetchIteration: ReturnType<typeof vi.fn>} => ({
    search: vi.fn(async (search) => pageOf(failures, search)),
    fetchIteration: vi.fn(async (id: string) => parents[id] as never),
    findByNumber: vi.fn(),
})

const leafIds = (targets: {failedChains: {id: string}[][]}) => targets.failedChains.map((chain) => chain[chain.length - 1].id)

const nestedFailure = (id: string, customerIndex: number) => ({
    ...iteration(id, `customer-${customerIndex + 1}`, "per_invoice", 6),
    loopRun: {taskId: "per_invoice", index: 6, value: "7", parent: embeddedParent(`customer-${customerIndex + 1}`, "root", "per_customer", customerIndex)},
})

describe("collectFailedTargets", () => {
    it("shouldIncludeTopLevelFailedIterationsWithTheRootOnly", async () => {
        const deps = depsFor([iteration("amer", "root", "per_region", 1)])

        const targets = await collectFailedTargets(root, deps)

        expect(targets.executionIds).toEqual(["root", "amer"])
        expect(targets.chains["amer"]).toEqual([{id: "amer", taskId: "per_region", number: 2, value: "2"}])
        expect(targets).toMatchObject({offset: 0, end: 1, total: 1})
    })

    it("shouldIncludeNestedFailuresUnderSuccessfulParentsWithTheirAncestors", async () => {
        const failures = [nestedFailure("f13", 12), nestedFailure("f26", 25), nestedFailure("f39", 38)]
        const deps = depsFor(failures)

        const targets = await collectFailedTargets(root, deps)

        expect(targets.executionIds).toEqual(["root", "customer-13", "f13", "customer-26", "f26", "customer-39", "f39"])
        expect(targets.chains["f26"].map((node) => `${node.taskId}:${node.number}`)).toEqual(["per_customer:26", "per_invoice:7"])
        expect(leafIds(targets)).toEqual(["f13", "f26", "f39"])
        expect(deps.fetchIteration).not.toHaveBeenCalled()
    })

    it("shouldFetchAParentThatIsNotEmbeddedOnlyOnce", async () => {
        const bare = (id: string) => ({...iteration(id, "customer-13", "per_invoice", 6), loopRun: {taskId: "per_invoice", index: 6}})
        const deps = depsFor([bare("a"), bare("b")], {"customer-13": embeddedParent("customer-13", "root", "per_customer", 12)})

        const targets = await collectFailedTargets(root, deps)

        expect(deps.fetchIteration).toHaveBeenCalledTimes(1)
        expect(targets.executionIds).toEqual(["root", "customer-13", "a", "b"])
    })

    it("shouldDropFailuresOfAnotherExecutionOfTheSameFlow", async () => {
        const stranger = {...iteration("s", "other-iteration", "per_invoice", 1), loopRun: {taskId: "per_invoice", index: 1, parent: {id: "other-iteration", parentId: "other-root", loopRun: {taskId: "per_customer", index: 0}}}}
        const deps = depsFor([stranger, nestedFailure("f13", 12)], {"other-root": {id: "other-root"}})

        const targets = await collectFailedTargets(root, deps)

        expect(targets.executionIds).toEqual(["root", "customer-13", "f13"])
    })

    it("shouldStopAtTheExecutionIdBudgetAndCarryTheOffsetToTheNextBatch", async () => {
        const failures = Array.from({length: 10}, (_, index) => iteration(`f${index}`, "root", "per_region", index))
        const deps = depsFor(failures)
        const limit = executionIdsUrlLength(["root", "f0", "f1", "f2"])

        const first = await collectFailedTargets(root, deps, {limit})
        const second = await collectFailedTargets(root, deps, {limit, offset: first.end})

        expect(first.executionIds).toEqual(["root", "f0", "f1", "f2"])
        expect(first).toMatchObject({offset: 0, end: 3, total: 10})
        expect(second.executionIds).toEqual(["root", "f3", "f4", "f5"])
        expect(second).toMatchObject({offset: 3, end: 6, total: 10})
    })

    it("shouldVisitEveryFailedIterationExactlyOnceAcrossBatchesAndPages", async () => {
        const failures = Array.from({length: 250}, (_, index) => iteration(`f${index}`, "root", "per_region", index))
        const deps = depsFor(failures)
        const limit = executionIdsUrlLength(["root", ...failures.slice(0, 40).map(({id}) => id)])
        const visited: string[] = []
        let offset = 0

        for (let guard = 0; guard < 50 && offset < 250; guard++) {
            const targets = await collectFailedTargets(root, deps, {limit, offset})
            expect(targets.end).toBeGreaterThan(offset)
            visited.push(...leafIds(targets))
            offset = targets.end
        }

        expect(offset).toBe(250)
        expect(visited).toEqual(failures.map(({id}) => id))
    })

    it("shouldResumeInTheMiddleOfAPageAndStopAtThePageEnd", async () => {
        const failures = Array.from({length: 300}, (_, index) => iteration(`f${index}`, "root", "per_region", index))
        const deps = depsFor(failures)

        const targets = await collectFailedTargets(root, deps, {offset: 148})

        expect(deps.search).toHaveBeenCalledTimes(1)
        expect(deps.search).toHaveBeenCalledWith(expect.objectContaining({page: 2, size: 100, state: "FAILED"}))
        expect(leafIds(targets)[0]).toBe("f148")
        expect(leafIds(targets)).toHaveLength(52)
        expect(targets).toMatchObject({offset: 148, end: 200, total: 300})
    })

    it("shouldEndTheLastBatchAtTheTotal", async () => {
        const failures = Array.from({length: 5}, (_, index) => iteration(`f${index}`, "root", "per_region", index))

        const targets = await collectFailedTargets(root, depsFor(failures), {offset: 3})

        expect(leafIds(targets)).toEqual(["f3", "f4"])
        expect(targets).toMatchObject({offset: 3, end: 5, total: 5})
    })

    it("shouldKeepReadingPagesWhenAPageHoldsNoIterationOfThisExecution", async () => {
        const strangers = Array.from({length: 100}, (_, index) => ({...iteration(`s${index}`, "other", "per_invoice", index), parentId: undefined}))
        const deps = depsFor([...strangers, iteration("f0", "root", "per_region", 0), iteration("f1", "root", "per_region", 1)])

        const targets = await collectFailedTargets(root, deps)

        expect(deps.search).toHaveBeenCalledTimes(2)
        expect(leafIds(targets)).toEqual(["f0", "f1"])
        expect(targets).toMatchObject({offset: 0, end: 102, total: 102})
    })

    it("shouldAlwaysAdvanceEvenWhenASingleChainExceedsTheBudget", async () => {
        const deps = depsFor([iteration("f0", "root", "per_region", 0), iteration("f1", "root", "per_region", 1)])

        const targets = await collectFailedTargets(root, deps, {limit: 1})

        expect(leafIds(targets)).toEqual(["f0"])
        expect(targets.end).toBe(1)
    })

    it("shouldReportTheTotalOfTheFailedSearch", async () => {
        const deps = depsFor([], {})
        deps.search = vi.fn(async () => ({results: [nestedFailure("f13", 12)], total: 500}))

        const targets = await collectFailedTargets(root, deps)

        expect(targets).toMatchObject({offset: 0, end: 1, total: 500})
    })
})

describe("collectScopedTargets", () => {
    const window = {startDate: "2026-10-09T09:49:14Z", endDate: "2026-10-09T09:49:16Z"}
    const customer = async (_parent: string, taskId: string, number: number) => ({id: `customer-${number}`, number, value: String(number), state: "SUCCESS", taskId, ...window})
    const customer13 = embeddedParent("customer-13", "root", "per_customer", 12)
    const invoiceUnder13 = (id: string, index: number) => ({
        ...iteration(id, "customer-13", "per_invoice", index),
        loopRun: {taskId: "per_invoice", index, value: String(index + 1), parent: customer13},
    })

    it("shouldSearchTheFailedIterationsInsideTheTimeWindowOfTheDeepestScope", async () => {
        const deps = depsFor([invoiceUnder13("f13", 6)])
        deps.findByNumber = vi.fn(customer)

        const targets = await collectScopedTargets(root, [{taskId: "per_customer", number: 13}], deps)

        expect(deps.search).toHaveBeenCalledWith(expect.objectContaining({root: expect.objectContaining(window), state: "FAILED"}))
        expect(targets.executionIds).toEqual(["root", "customer-13", "f13"])
        expect(targets.chains["f13"].map((node) => `${node.taskId}:${node.number}`)).toEqual(["per_customer:13", "per_invoice:7"])
        expect(targets.scope).toEqual([{id: "customer-13", taskId: "per_customer", number: 13, value: "13"}])
        expect(targets).toMatchObject({offset: 0, end: 1, total: 1})
    })

    it("shouldFindAFailedIterationUnderSuccessfulIntermediateIterationsWhenScopedAboveThem", async () => {
        const invoice = {
            ...iteration("inv-7", "customer-13", "per_invoice", 6),
            loopRun: {
                taskId: "per_invoice",
                index: 6,
                value: "7",
                parent: {id: "customer-13", parentId: "region-2", loopRun: {taskId: "per_customer", index: 12, value: "13", parent: embeddedParent("region-2", "root", "per_region", 1)}},
            },
        }
        const deps = depsFor([invoice])
        deps.findByNumber = vi.fn(async (_parent: string, taskId: string, number: number) => ({id: `region-${number}`, number, value: String(number), state: "SUCCESS", taskId}))

        const targets = await collectScopedTargets(root, [{taskId: "per_region", number: 2}], deps)

        expect(targets.executionIds).toEqual(["root", "region-2", "customer-13", "inv-7"])
        expect(targets.chains["inv-7"].map((node) => `${node.taskId}:${node.number}`)).toEqual(["per_region:2", "per_customer:13", "per_invoice:7"])
    })

    it("shouldIgnoreWindowFailuresThatDoNotPassThroughTheScope", async () => {
        const deps = depsFor([nestedFailure("f13", 12)])
        deps.findByNumber = vi.fn(customer)

        const targets = await collectScopedTargets(root, [{taskId: "per_customer", number: 26}], deps)

        expect(targets.executionIds).toEqual(["root", "customer-26"])
        expect(targets).toMatchObject({offset: 0, end: 1, total: 1})
    })

    it("shouldCarryTheOffsetToTheNextBatchWhenTheBudgetCutsAScopedPage", async () => {
        const failures = Array.from({length: 30}, (_, index) => invoiceUnder13(`f${index}`, index))
        const deps = depsFor(failures)
        deps.findByNumber = vi.fn(customer)
        const limit = executionIdsUrlLength(["root", "customer-13", ...failures.slice(0, 10).map(({id}) => id)])
        const entries = [{taskId: "per_customer", number: 13}]

        const first = await collectScopedTargets(root, entries, deps, {limit})
        const second = await collectScopedTargets(root, entries, deps, {limit, offset: first.end})

        expect(first).toMatchObject({offset: 0, end: 10, total: 30})
        expect(second.end).toBeGreaterThan(10)
        expect(leafIds(second)).toEqual(failures.slice(10, second.end).map(({id}) => id))
        expect(second).toMatchObject({offset: 10, total: 30})
    })

    it("shouldRejectAScopeThatNoLongerExists", async () => {
        const deps = depsFor([])
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
        const deps = depsFor(candidates)
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
        const deps = depsFor(candidates, {"customer-13": embeddedParent("customer-13", "root", "per_customer", 12)})
        const cache = createChainCache()

        await collectFailedTargets(root, deps, {cache})
        await collectFailedTargets(root, deps, {cache})

        expect(deps.fetchIteration).toHaveBeenCalledTimes(1)
    })
})

describe("request budget", () => {
    it("shouldStopFetchingParentsAtTheRequestCapAndResumeAtTheFirstUnresolvedIteration", async () => {
        const bare = (id: string) => ({...iteration(id, `p-${id}`, "per_invoice", 6), loopRun: {taskId: "per_invoice", index: 6}})
        const deps = depsFor(Array.from({length: 30}, (_, index) => bare(`i${index}`)))
        deps.fetchIteration = vi.fn(async (id: string) => embeddedParent(id, "root", "per_customer", 1) as never)

        const targets = await collectFailedTargets(root, deps, {maxRequests: 12})

        expect(deps.fetchIteration).toHaveBeenCalledTimes(11)
        expect(leafIds(targets)).toHaveLength(11)
        expect(targets).toMatchObject({offset: 0, end: 11, total: 30})
    })
})

describe("logUrlReserve", () => {
    const levelParams = {"filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"}
    const sentUrls: string[] = []

    beforeEach(() => {
        sentUrls.length = 0
        configureClient({
            baseUrl: "http://localhost",
            fetch: async (input: RequestInfo | URL) => {
                sentUrls.push((input as Request).url)
                return new Response(JSON.stringify({results: [], total: 0}), {headers: {"content-type": "application/json"}})
            },
        })
    })

    const queryOf = (url: string) => url.slice(url.indexOf("?") + 1)

    it("shouldGrowWithTheLevelFilterAndTheTextSearch", () => {
        const base = {levelParams, kinds: ["NORMAL", "LOOP"]}

        expect(logUrlReserve({...base, q: "a long search text"})).toBeGreaterThan(logUrlReserve(base))
    })

    it("shouldMeasureTheExecutionIdsWithTheSerializerTheClientIsConfiguredWith", () => {
        client.setConfig({querySerializer: () => "x".repeat(10)})

        expect(executionIdsUrlLength(["a", "b"])).toBe(10)
    })

    it("shouldAccountForExactlyTheQueryTheSdkSendsForALogSearch", async () => {
        const ids = ["root", ...Array.from({length: 40}, (_, index) => `${"x".repeat(22)}${index}`)]
        const search = {executionIds: ids, kinds: ["NORMAL", "LOOP"], levelParams, q: "needle é", page: 1}

        await searchMergedLogs(search)

        expect(logUrlReserve(search) + executionIdsUrlLength(ids)).toBe(LOG_URL_MARGIN + queryOf(sentUrls[0]).length)
    })

    it("shouldKeepTheFullUrlUnderTheLimitWithTheCursorMargin", async () => {
        const failures = Array.from({length: 300}, (_, index) => iteration(`${"x".repeat(22)}${index}`, "root", "per_region", index))
        const deps = depsFor(failures)
        const search = {kinds: ["NORMAL", "LOOP"], levelParams, q: "needle", page: 1, cursor: "c".repeat(300)}
        const reserve = logUrlReserve(search)

        const targets = await collectFailedTargets(root, deps, {reserve})
        await searchMergedLogs({...search, executionIds: targets.executionIds})

        expect(reserve + executionIdsUrlLength(targets.executionIds)).toBeLessThanOrEqual(MAX_LOG_URL_LENGTH)
        expect(sentUrls[0].length).toBeLessThanOrEqual(MAX_LOG_URL_LENGTH)
        expect(targets.end).toBeLessThan(targets.total)
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
