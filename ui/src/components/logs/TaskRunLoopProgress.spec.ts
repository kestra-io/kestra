import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent} from "vue"
import "../../utils/global"
import TaskRunLoopProgress from "./TaskRunLoopProgress.vue"

vi.mock("@kestra-io/design-system", () => ({
    State: {color: () => ({SUCCESS: "green", FAILED: "red"})},
}))

const KsProgressStub = defineComponent({
    name: "KsProgress",
    props: {percentage: {type: Number, default: undefined}},
    template: "<div data-test=\"progress\" :data-percentage=\"percentage\" />",
})

const KsButtonStub = defineComponent({
    name: "KsButton",
    template: "<button data-test=\"pill\"><slot /></button>",
})

function mountProgress(loopOutputsByTaskRunId: Record<string, any>) {
    return mount(TaskRunLoopProgress, {
        props: {
            executionId: "exec-1",
            currentTaskRunId: "taskrun-1",
            taskId: "loop",
            loopOutputsByTaskRunId,
        },
        global: {
            stubs: {KsProgress: KsProgressStub, KsButton: KsButtonStub},
        },
    })
}

describe("TaskRunLoopProgress", () => {
    it("should hide the progress bar instead of showing NaN% when outputs are not loaded yet", () => {
        const wrapper = mountProgress({})

        expect(wrapper.find("[data-test=progress]").exists()).toBe(false)
        expect(wrapper.text()).not.toContain("NaN")
    })

    it("should show the failed iteration alongside successes once outputs are loaded", () => {
        const wrapper = mountProgress({
            "taskrun-1": {
                iterationCount: 3,
                terminatedIterations: {SUCCESS: 1, FAILED: 1},
            },
        })

        const progress = wrapper.find("[data-test=progress]")
        expect(Number(progress.attributes("data-percentage"))).toBeCloseTo(200 / 3)

        const pills = wrapper.findAll("[data-test=pill]")
        expect(pills).toHaveLength(2)
        expect(pills[0].text()).toBe("1 Success")
        expect(pills[1].text()).toBe("1 Failed")
    })
})
