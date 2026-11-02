import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {createPinia, setActivePinia} from "pinia"
import {useExecutionsStore} from "../../../stores/executions"
import type {LoopRef} from "./ganttTree"
import {useLoopDrillDown} from "./useLoopDrillDown"

const loop: LoopRef = {parentExecutionId: "root", taskId: "per_region", namespace: "company.team", flowId: "monthly_close"}

const execution = (index: number, state = "SUCCESS") => ({
    id: `it-${index}`,
    state: {current: state, histories: []},
    loopRun: {index, value: `v${index}`},
})

let findExecutions: ReturnType<typeof vi.spyOn>

function respondWith(total: number) {
    findExecutions.mockImplementation((async (options: Record<string, unknown>) => {
        if (options.onlyTotal) return total
        const start = ((options.page as number) - 1) * (options.size as number)
        return {results: [execution(start)], total}
    }) as never)
}

const lastListCall = () => (findExecutions.mock.calls as Array<[Record<string, unknown>]>).map(([options]) => options).filter(options => !options.onlyTotal).at(-1)!

beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")
})

afterEach(() => {
    vi.useRealTimers()
})

describe("useLoopDrillDown", () => {
    it("shouldBackToPageOneAndFilterByExactValueWhenSearchSettles", async () => {
        respondWith(35)
        const drillDown = useLoopDrillDown(loop)
        await vi.advanceTimersByTimeAsync(0)
        drillDown.goToPage(3)
        await vi.advanceTimersByTimeAsync(0)
        expect(lastListCall().page).toBe(3)

        drillDown.search.value = " APAC "
        await nextTick()
        await vi.advanceTimersByTimeAsync(100)
        expect(lastListCall().page).toBe(3)
        await vi.advanceTimersByTimeAsync(300)

        expect(lastListCall()).toMatchObject({page: 1, "filters[loopRunValue][EQUALS]": "APAC"})
        expect(drillDown.page.value).toBe(1)
    })

    it("shouldRefetchFromPageOneWhenStateFilterChanges", async () => {
        respondWith(35)
        const drillDown = useLoopDrillDown(loop)
        await vi.advanceTimersByTimeAsync(0)
        drillDown.goToPage(2)
        await vi.advanceTimersByTimeAsync(0)

        drillDown.state.value = "failed"
        await nextTick()
        await vi.advanceTimersByTimeAsync(0)

        expect(lastListCall()).toMatchObject({page: 1, "filters[state][IN]": ["FAILED"]})
    })

    it("shouldKeepTheLatestResponseWhenAnEarlierRequestResolvesLast", async () => {
        const resolvers: Array<(value: unknown) => void> = []
        findExecutions.mockImplementation(((options: Record<string, unknown>) => {
            if (options.onlyTotal) return Promise.resolve(2)
            return new Promise(resolve => resolvers.push(resolve))
        }) as never)
        const drillDown = useLoopDrillDown(loop)
        await vi.advanceTimersByTimeAsync(0)
        drillDown.state.value = "failed"
        await nextTick()
        await vi.advanceTimersByTimeAsync(0)
        expect(resolvers).toHaveLength(2)

        resolvers[1]({results: [execution(7, "FAILED")], total: 1})
        await vi.advanceTimersByTimeAsync(0)
        resolvers[0]({results: [execution(0), execution(1)], total: 2})
        await vi.advanceTimersByTimeAsync(0)

        expect(drillDown.rows.value.map(row => row.id)).toEqual(["it-7"])
        expect(drillDown.total.value).toBe(1)
        expect(drillDown.loading.value).toBe(false)
    })

    it("shouldClampPageWithinTheAvailablePages", async () => {
        respondWith(25)
        const drillDown = useLoopDrillDown(loop)
        await vi.advanceTimersByTimeAsync(0)
        expect(drillDown.pageCount.value).toBe(3)

        drillDown.goToPage(99)
        expect(drillDown.page.value).toBe(3)
        drillDown.goToPage(0)
        expect(drillDown.page.value).toBe(1)
    })
})
