import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent} from "vue"
import "../../utils/global"
import TaskRunLoopProgress from "./TaskRunLoopProgress.vue"

vi.mock("vue-i18n", () => ({
    useI18n: () => ({
        t: (key: string) => key === "in flight" ? "In flight" : key === "not started" ? "Not started" : key
    })
}))

vi.mock("@kestra-io/design-system", () => ({
    State: {
        color: () => ({SUCCESS: "green", FAILED: "red", RUNNING: "purple"}),
        arrayAllStates: () => [
            {name: "SUCCESS"}, {name: "WARNING"}, {name: "FAILED"},
            {name: "KILLED"}, {name: "CANCELLED"}, {name: "RETRIED"},
            {name: "SKIPPED"}, {name: "RESUBMITTED"}, {name: "RUNNING"}, {name: "CREATED"}
        ],
        isTerminated: (state: string) => ["SUCCESS", "WARNING", "FAILED", "KILLED", "CANCELLED", "RETRIED", "SKIPPED", "RESUBMITTED"].includes(state)
    },
}))

const KsProgressStub = defineComponent({
    name: "KsProgress",
    props: {percentage: {type: Number, default: undefined}, format: {type: Function, default: undefined}},
    template: "<div data-test=\"progress\" :data-percentage=\"percentage\" :data-format=\"format ? format(percentage) : ''\" />",
})

const KsButtonStub = defineComponent({
    name: "KsButton",
    template: "<button data-test=\"pill\"><slot /></button>",
})

function mountProgress(loopOutputsByTaskRunId: Record<string, {iterationCount: number; terminatedIterations?: Record<string, number>; runningIterations?: number}>, loopTaskState?: string) {
    return mount(TaskRunLoopProgress, {
        props: {
            executionId: "exec-1",
            currentTaskRunId: "taskrun-1",
            taskId: "loop",
            loopOutputsByTaskRunId,
            loopTaskState,
        },
        global: {
            stubs: {KsProgress: KsProgressStub, KsButton: KsButtonStub, RouterLink: true},
        },
    })
}

describe("TaskRunLoopProgress", () => {
    it("should hide the progress bar instead of showing NaN% when outputs are not loaded yet", () => {
        const wrapper = mountProgress({})

        expect(wrapper.find("[data-test=progress]").exists()).toBe(false)
        expect(wrapper.text()).not.toContain("NaN")
    })

    it("should display partial percentage correctly (66.6%)", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 3,
                terminatedIterations: {SUCCESS: 2},
            },
        })

        const progress = wrapper.find("[data-test=progress]")
        expect(Number(progress.attributes("data-percentage"))).toBeCloseTo(66.666)
        expect(progress.attributes("data-format")).toBe("66.6%")
    })

    it("should floor near completion (2999/3000 to 99.9%)", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 3000,
                terminatedIterations: {SUCCESS: 2999},
            },
        })

        const progress = wrapper.find("[data-test=progress]")
        // Math is ~99.9666...
        expect(Number(progress.attributes("data-percentage"))).toBeCloseTo(99.966)
        expect(progress.attributes("data-format")).toBe("99.9%")
    })

    it("should display exactly 100.0% only upon full completion", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 3000,
                terminatedIterations: {SUCCESS: 3000},
            },
        })

        const progress = wrapper.find("[data-test=progress]")
        expect(Number(progress.attributes("data-percentage"))).toBe(100)
        expect(progress.attributes("data-format")).toBe("100.0%")
    })

    it("should clamp progress exactly to 100% when overcompleted", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 10,
                terminatedIterations: {SUCCESS: 12},
            },
        })

        const progress = wrapper.find("[data-test=progress]")
        expect(Number(progress.attributes("data-percentage"))).toBe(100)
        expect(progress.attributes("data-format")).toBe("100.0%")
    })

    it("should show the failed iteration alongside successes once outputs are loaded", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 3,
                terminatedIterations: {SUCCESS: 1, FAILED: 1},
            },
        })

        const pills = wrapper.findAll("[data-test=pill]")
        expect(pills).toHaveLength(3)
        expect(pills[0].text()).toBe("1 Success")
        expect(pills[1].text()).toBe("1 Failed")
        expect(pills[2].text()).toBe("1 Not started")
    })

    it("should show in-flight pill when running iterations exist", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 10,
                terminatedIterations: {SUCCESS: 3},
                runningIterations: 2,
            },
        })

        const pills = wrapper.findAll("[data-test=pill]")
        expect(pills).toHaveLength(3)
        expect(pills[0].text()).toBe("3 Success")
        expect(pills[1].text()).toBe("2 In flight")
        expect(pills[2].text()).toBe("5 Not started")
    })

    it("should hide in-flight and not started pills when parent loop is terminal", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 100,
                terminatedIterations: {SUCCESS: 10},
                runningIterations: 5,
            },
        }, "KILLED")

        const pills = wrapper.findAll("[data-test=pill]")
        // Active pills should vanish because parent is KILLED
        expect(pills).toHaveLength(1)
        expect(pills[0].text()).toBe("10 Success")
    })

    it("should clamp in-flight count to remaining iterations", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 5,
                terminatedIterations: {SUCCESS: 4},
                runningIterations: 3,
            },
        })

        const pills = wrapper.findAll("[data-test=pill]")
        expect(pills).toHaveLength(2)
        expect(pills[0].text()).toBe("4 Success")
        expect(pills[1].text()).toBe("1 In flight")
    })
})
