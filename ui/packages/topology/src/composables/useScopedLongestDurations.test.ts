import {afterEach, describe, expect, it, vi} from "vitest"
import {defineComponent, reactive} from "vue"
import {mount} from "@vue/test-utils"
import {useScopedLongestDurations} from "./useScopedLongestDurations"

const run = (id: string, start: number, end?: number) => ({
    id,
    taskId: id,
    state: {
        current: end ? "SUCCESS" : "RUNNING",
        histories: [
            {date: new Date(start).toISOString(), state: "RUNNING"},
            ...(end ? [{date: new Date(end).toISOString(), state: "SUCCESS"}] : []),
        ],
    },
})

function setup(executions: Record<string, {id: string; taskRunList: ReturnType<typeof run>[]}>) {
    const state = reactive(executions)
    let durations!: ReturnType<typeof useScopedLongestDurations>
    const wrapper = mount(defineComponent({
        setup() {
            durations = useScopedLongestDurations(() => Object.values(state))
            return () => null
        },
    }))
    return {state, durations, wrapper}
}

describe("useScopedLongestDurations", () => {
    afterEach(() => vi.useRealTimers())

    it("shouldComputeTheLongestRunOncePerExecutionId", () => {
        const {durations} = setup({
            a: {id: "it-1", taskRunList: [run("x", 0, 1000), run("y", 0, 4000)]},
            b: {id: "it-2", taskRunList: [run("x", 0, 2000)]},
        })

        expect(durations.value).toEqual({"it-1": 4000, "it-2": 2000})
    })

    it("shouldFollowAReplacedTaskRunListAndDropRemovedExecutions", async () => {
        const {state, durations} = setup({a: {id: "it-1", taskRunList: [run("x", 0, 1000)]}})

        state.a = {id: "it-1", taskRunList: [run("x", 0, 5000)]}
        await Promise.resolve()
        expect(durations.value).toEqual({"it-1": 5000})

        delete state.a
        await Promise.resolve()
        expect(durations.value).toEqual({})
    })

    it("shouldKeepTickingWhileAScopedRunIsStillRunning", async () => {
        vi.useFakeTimers()
        vi.setSystemTime(10_000)
        const {durations} = setup({a: {id: "it-1", taskRunList: [run("x", 9_000)]}})
        const before = durations.value["it-1"]

        vi.advanceTimersByTime(2_000)
        await Promise.resolve()

        expect(durations.value["it-1"]).toBeGreaterThan(before)
    })
})
