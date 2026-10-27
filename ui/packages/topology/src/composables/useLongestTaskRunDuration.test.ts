import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick, ref} from "vue"
import {mount} from "@vue/test-utils"
import {useLongestTaskRunDuration} from "./useLongestTaskRunDuration"
import type {TaskRunLike} from "../misc/durationBreakdown"

const START = Date.UTC(2026, 0, 1)

function run(...histories: [number, string][]): TaskRunLike {
    return {state: {histories: histories.map(([offset, state]) => ({date: START + offset, state}))}}
}

function mountComposable(taskRuns: TaskRunLike[], interval = 500) {
    const runs = ref(taskRuns)
    let longest!: ReturnType<typeof useLongestTaskRunDuration>
    const wrapper = mount(defineComponent({
        setup() {
            longest = useLongestTaskRunDuration(runs, interval)
            return () => h("div")
        },
    }))
    return {wrapper, runs, longest}
}

describe("useLongestTaskRunDuration", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(START + 8_000)
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("should keep growing while a task run is still running, so finished runs shrink against it", async () => {
        const {longest} = mountComposable([
            run([0, "RUNNING"], [2_000, "SUCCESS"]),
            run([2_000, "RUNNING"]),
        ])
        expect(longest.value).toBe(6_000)

        vi.advanceTimersByTime(12_000)
        await nextTick()

        expect(longest.value).toBe(18_000)
    })

    it("should stop ticking once every task run is terminal", async () => {
        const {runs, longest} = mountComposable([run([0, "RUNNING"])])

        runs.value = [run([0, "RUNNING"], [8_000, "SUCCESS"])]
        await nextTick()
        vi.advanceTimersByTime(5_000)
        await nextTick()

        expect(longest.value).toBe(8_000)
        expect(vi.getTimerCount()).toBe(0)
    })

    it("should start ticking again when a task run starts after every run was terminal", async () => {
        const {runs, longest} = mountComposable([run([0, "RUNNING"], [2_000, "SUCCESS"])])
        expect(vi.getTimerCount()).toBe(0)

        runs.value = [...runs.value, run([8_000, "RUNNING"])]
        await nextTick()
        vi.advanceTimersByTime(4_000)
        await nextTick()

        expect(vi.getTimerCount()).toBe(1)
        expect(longest.value).toBe(4_000)
    })

    it("should clear its ticker when the owner unmounts", () => {
        const {wrapper} = mountComposable([run([0, "RUNNING"])])
        expect(vi.getTimerCount()).toBe(1)

        wrapper.unmount()

        expect(vi.getTimerCount()).toBe(0)
    })
})
