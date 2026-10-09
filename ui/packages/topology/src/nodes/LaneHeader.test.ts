import {describe, expect, it} from "vitest"
import {computed} from "vue"
import LaneHeader from "./LaneHeader.vue"
import {EXECUTION_INJECTION_KEY, LOOP_LANES_INJECTION_KEY, SUBFLOWS_EXECUTIONS_INJECTION_KEY} from "../injectionKeys"
import {EVENTS} from "../utils/constants"
import type {LoopLaneData} from "../utils/loopOutcome"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import en from "../../../../src/translations/en.json"

const LOOP = {uid: "per_region", task: {id: "per_region", type: "io.kestra.plugin.core.flow.Loop"}}

const lane = (overrides: Partial<LoopLaneData>): LoopLaneData => ({
    taskId: "per_region",
    status: "ready",
    parentScoped: true,
    ...overrides,
})

function mountLane(lanes: Record<string, LoopLaneData>, options: {layout?: "bar" | "card"; slots?: Record<string, string>} = {}) {
    return i18nMount(LaneHeader, {
        locales: en,
        slots: options.slots,
        props: {taskNode: LOOP, color: "flowable-task", layout: options.layout, childTaskIds: ["a", "b"], executionId: "execution-id", isReadOnly: true},
        global: {
            stubs: {NodeMenu: true},
            provide: {
                [EXECUTION_INJECTION_KEY as symbol]: computed(() => ({id: "execution-id", taskRunList: []})),
                [SUBFLOWS_EXECUTIONS_INJECTION_KEY as symbol]: computed(() => ({})),
                [LOOP_LANES_INJECTION_KEY as symbol]: computed(() => lanes),
            },
        },
    })
}

const text = (wrapper: ReturnType<typeof mountLane>, selector: string) => wrapper.find(`[data-test='${selector}']`).text()

describe("LaneHeader loop outcome", () => {
    it("shouldReplaceTheChildCountWithTheFailedChip", () => {
        const wrapper = mountLane({per_region: lane({iterationCount: 4, terminatedIterations: {FAILED: 1, SUCCESS: 3}, state: "FAILED"})})

        expect(text(wrapper, "loop-outcome-full")).toBe("1 of 4 failed")
        expect(text(wrapper, "loop-outcome-mid")).toBe("1 / 4")
        expect(text(wrapper, "loop-outcome-min")).toBe("1")
        expect(wrapper.find(".lane-count").exists()).toBe(false)
        expect(wrapper.find(".lane-state").exists()).toBe(false)
    })

    it("shouldSayWhatNeverStarted", () => {
        const wrapper = mountLane({per_region: lane({iterationCount: 4, terminatedIterations: {FAILED: 1, SUCCESS: 1}, state: "FAILED"})})

        expect(text(wrapper, "loop-outcome-full")).toBe("1 failed · 2 not started of 4")
    })

    it("shouldSayIterationsWhenNoneFailed", () => {
        const wrapper = mountLane({per_region: lane({iterationCount: 4, terminatedIterations: {SUCCESS: 4}, state: "SUCCESS"})})

        expect(text(wrapper, "loop-outcome-full")).toBe("4 iterations")
        expect(wrapper.find("button[data-test='loop-outcome']").exists()).toBe(false)
    })

    it("shouldShowProgressAndNoRedundantRunningState", () => {
        const wrapper = mountLane({per_region: lane({iterationCount: 4, runningIterations: 1, terminatedIterations: {FAILED: 1, SUCCESS: 1}, state: "RUNNING"})})

        expect(text(wrapper, "loop-outcome-full")).toBe("2 of 4 done · 1 failed")
        expect(wrapper.find(".lane-state").exists()).toBe(false)
    })

    it("shouldShowTheLoopStateTheChipCannotSay", () => {
        const wrapper = mountLane({per_region: lane({iterationCount: 4, terminatedIterations: {SUCCESS: 2}, state: "KILLED"})})

        expect(text(wrapper, "loop-outcome-full")).toBe("2 of 4 done")
        expect(wrapper.find(".lane-state-text").text()).toBe("KILLED")
    })

    it("shouldEmitWhenTheFailedChipIsClicked", async () => {
        const wrapper = mountLane({per_region: lane({iterationCount: 4, terminatedIterations: {FAILED: 1, SUCCESS: 3}, state: "FAILED"})})

        await wrapper.find("button[data-test='loop-outcome']").trigger("click")

        expect(wrapper.emitted(EVENTS.LOOP_SCOPE_FAILED)?.[0]).toEqual([{uid: "per_region"}])
    })

    it("shouldMarkALoopThatNeverRan", () => {
        const wrapper = mountLane({per_region: lane({status: "not-started"})}, {slots: {loopScope: "<span data-test='picker' />"}})

        expect(text(wrapper, "loop-not-run")).toBe("Not run")
        expect(wrapper.find("[data-test='picker']").exists()).toBe(false)
    })

    it("shouldStepTheIterationWithBracketKeys", async () => {
        const wrapper = mountLane({per_region: lane({iterationCount: 4, terminatedIterations: {SUCCESS: 4}, state: "SUCCESS"})})

        await wrapper.find("[data-test='topology-lane-header']").trigger("keydown", {key: "]"})
        await wrapper.find("[data-test='topology-lane-header']").trigger("keydown", {key: "["})

        expect(wrapper.emitted(EVENTS.LOOP_STEP)).toEqual([[{uid: "per_region", delta: 1}], [{uid: "per_region", delta: -1}]])
    })

    it("shouldMoveTheChildCountToTheTypeLineOnACollapsedCard", () => {
        const wrapper = mountLane({per_region: lane({iterationCount: 4, terminatedIterations: {SUCCESS: 4}, state: "SUCCESS"})}, {layout: "card"})

        expect(wrapper.find(".lane-type").text()).toContain("2 tasks")
    })

    it("shouldKeepTheChildCountOutsideExecutionView", () => {
        const wrapper = mountLane({})

        expect(wrapper.find("[data-test='loop-outcome']").exists()).toBe(false)
        expect(wrapper.find(".lane-count").text()).toBe("2 tasks")
    })
})
