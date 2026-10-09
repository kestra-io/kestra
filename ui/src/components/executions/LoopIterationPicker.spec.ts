import {describe, expect, it} from "vitest"
import type {LoopLaneData} from "@kestra-io/topology"
import LoopIterationPicker from "./LoopIterationPicker.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"

const lane = (overrides: Partial<LoopLaneData>): LoopLaneData => ({
    taskId: "per_quarter",
    status: "ready",
    iterationCount: 4,
    terminatedIterations: {SUCCESS: 4},
    parentScoped: true,
    ...overrides,
})

const mountPicker = (data: LoopLaneData) =>
    i18nMount(LoopIterationPicker, {locales: en, props: {lane: data, hostExecutionId: "execution-id"}})

describe("LoopIterationPicker", () => {
    it("shouldDisableTheNestedPickerUntilTheParentLoopIsScoped", () => {
        const wrapper = mountPicker(lane({parentScoped: false, parentTaskId: "per_region", status: "nested"}))

        expect(wrapper.find("[data-test='loop-picker-disabled']").text()).toBe("Pick per_region first")
        expect(wrapper.find("[data-test='loop-picker-disabled']").attributes("disabled")).toBeDefined()
        expect(wrapper.find("[data-test='loop-picker-trigger']").exists()).toBe(false)
    })

    it("shouldOfferAllIterationsWhileUnscoped", () => {
        const wrapper = mountPicker(lane({}))

        expect(wrapper.find("[data-test='loop-picker-trigger']").text()).toBe("All iterations")
        expect(wrapper.find("[data-test='loop-stepper']").exists()).toBe(false)
    })

    it("shouldShowTheStepperOnceScopedAndEmitTheStep", async () => {
        const wrapper = mountPicker(lane({scopedNumber: 2}))

        expect(wrapper.find("[data-test='loop-picker-trigger']").text()).toBe("#2")
        expect(wrapper.find("[data-test='loop-stepper-label']").text()).toBe("2 of 4")

        await wrapper.find("[data-test='loop-step-next']").trigger("click")

        expect(wrapper.emitted("step")).toEqual([[1]])
    })

    it("shouldDisableTheStepperAtTheBounds", () => {
        const first = mountPicker(lane({scopedNumber: 1}))
        const last = mountPicker(lane({scopedNumber: 4}))

        expect(first.find("[data-test='loop-step-previous']").attributes("disabled")).toBeDefined()
        expect(last.find("[data-test='loop-step-next']").attributes("disabled")).toBeDefined()
    })
})
